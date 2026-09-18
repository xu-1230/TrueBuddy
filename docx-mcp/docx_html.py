"""docx → docx-wrapper HTML 渲染。

把 .docx 解析并渲染成与前端 docx-preview 同构的 HTML 结构：

    <div class="docx-wrapper">
      <section class="docx" style="padding: ...; width: ...pt; min-height: ...pt;">
        <article style="column-count: 1; column-gap: ...pt;">
          <p style="..."><span style="...">文本</span></p>
          <table style="...">
            <colgroup><col style="width: ...pt;"></colgroup>
            <tr style="height: ...pt;"><td style="..."><p>...</p></td></tr>
          </table>
        </article>
      </section>
    </div>

渲染范围：
    - 正文段落、表格（含嵌套表格、横向 colspan / 纵向 rowspan 合并单元格）
    - 段落对齐、首行/悬挂缩进、行距、段前段后距、分栏
    - 行内字符格式（中西文字体、字号、加粗、倾斜、下划线、删除线、颜色、上下标）
    - 多级编号 class（``docx-num-{level}-{numId}``，与 docx-preview 约定一致）
    - 内嵌图片（由 ``asset_resolver`` 决定输出 URL / data URI / 占位 ``<img>``）

不渲染：
    - 分页（整篇输出为单个 ``<section>``，不使用 Word 分节符）
    - 页眉页脚中的图形、域代码、批注等
"""

from __future__ import annotations

import hashlib
import html
from dataclasses import dataclass
from typing import Any, Callable, Optional

from docx import Document
from docx.oxml.ns import qn

__all__ = ["ImageAsset", "RenderResult", "render_docx", "render_docx_to_html"]

# --------------------------------------------------------------------------- #
# 基础工具
# --------------------------------------------------------------------------- #

# 无法用 CSS 表达、但 Word/WPS 会**优先采用**的东亚版式属性。
# 只把 pt 值写进 CSS 的话，WPS 会退回到绝对值排版，与 HTML 明显不一致。
_EA_SPACING_ATTRS = ("beforeLines", "afterLines")
_EA_IND_ATTRS = (
    "firstLineChars",
    "hangingChars",
    "leftChars",
    "startChars",
    "rightChars",
    "endChars",
)
_EA_RUN_ATTRS = ("ascii", "eastAsia", "hAnsi", "cs", "hint")

# 段落上的开关类属性：默认值与 Word 内置一致的不带，只带会"关掉"默认行为的。
# snapToGrid 默认是「对齐文档网格」，源文档显式关掉；漏掉它 WPS 会把每行吸附到
# 文档网格，行距被撑成网格间距的整数倍——这正是 WPS 与 HTML 显示不一致的主因。
_EA_PARA_FLAGS = ("snapToGrid", "adjustRightInd")

_ALIGN_MAP = {
    "both": "justify",
    "distribute": "justify",
    "centerContinuous": "center",
    "start": "left",
    "end": "right",
}

_VALIGN_MAP = {
    "top": "top",
    "center": "middle",
    "bottom": "bottom",
}


def _w(tag: str) -> str:
    """返回 ``w:`` 命名空间的完整标签名。"""
    return qn(f"w:{tag}")


def _local(tag: Any) -> str:
    """去掉标签的命名空间前缀。"""
    return str(tag).rsplit("}", 1)[-1]


def _wval(element: Any, name: str, default: Optional[str] = None) -> Optional[str]:
    """读取元素的 ``w:{name}`` 属性。

    注意：lxml 元素在没有子节点时布尔值为 False，因此这里一律使用 ``is None`` 判断。
    """
    if element is None:
        return default
    value = element.get(qn(f"w:{name}"))
    return default if value is None else value


def _num(value: float) -> str:
    """把数值格式化为不带多余 0 的字符串。"""
    text = f"{float(value):.2f}".rstrip("0").rstrip(".")
    return text if text else "0"


def _twips(value: Any) -> str:
    """缇（1/20 pt）→ pt。"""
    try:
        return f"{_num(int(value) / 20)}pt"
    except (TypeError, ValueError):
        return "0pt"


def _half_points(value: Any) -> str:
    """半磅（字号单位）→ pt。"""
    try:
        return f"{_num(int(value) / 2)}pt"
    except (TypeError, ValueError):
        return "0pt"


def _emu(value: Any) -> str:
    """EMU（1/12700 pt）→ pt。"""
    try:
        return f"{_num(int(value) / 12700)}pt"
    except (TypeError, ValueError):
        return "0pt"


def _length_pt(length: Any, default: float) -> float:
    """python-docx 的 Length → pt 浮点数。"""
    try:
        return float(length.pt)
    except Exception:  # noqa: BLE001 - Length 缺失时回退默认值
        return default


def _css(style: dict[str, str]) -> str:
    """把 CSS 字典序列化为 ``k: v;`` 形式。"""
    return " ".join(f"{key}: {value};" for key, value in style.items())


def _style_attr(style: dict[str, str]) -> str:
    """把 CSS 字典序列化为 `` style="..."`` 属性；空字典返回空串。"""
    if not style:
        return ""
    return f' style="{html.escape(_css(style), quote=True)}"'


def _pack(values: list[str]) -> str:
    """按 CSS 简写规则压缩四值列表：全等→单值，上下/左右相等→两值，否则四值。"""
    if not values:
        return ""
    if len(set(values)) == 1:
        return values[0]
    if len(values) == 4 and values[0] == values[2] and values[1] == values[3]:
        return f"{values[0]} {values[1]}"
    return " ".join(values)


# --------------------------------------------------------------------------- #
# 图片资产
# --------------------------------------------------------------------------- #

_IMAGE_EXT = {
    "image/png": ".png",
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/gif": ".gif",
    "image/bmp": ".bmp",
    "image/tiff": ".tiff",
    "image/webp": ".webp",
    "image/svg+xml": ".svg",
    "image/x-emf": ".emf",
    "image/emf": ".emf",
    "image/x-wmf": ".wmf",
}

ImageResolver = Callable[["ImageAsset"], Optional[str]]


@dataclass
class ImageAsset:
    """一张内嵌图片：内容寻址命名 + 原始字节 + 展示尺寸。

    渲染器只负责产出该对象，**不做任何编码或上传**；由 ``asset_resolver``
    决定它变成 URL、data URI 还是占位 ``<img>``。
    """

    r_id: str
    name: str
    mime: str
    blob: bytes
    width: Optional[str] = None
    height: Optional[str] = None

    @property
    def size(self) -> int:
        return len(self.blob)


@dataclass
class RenderResult:
    """一次渲染的产物：HTML 文本 + 去重后的图片资产 + 结构统计。"""

    html: str
    assets: list[ImageAsset]
    stats: dict[str, int]


def _asset_name(mime: str, blob: bytes) -> str:
    """按内容哈希生成文件名：同图复用、可永久缓存。"""
    ext = _IMAGE_EXT.get(mime) or "." + (mime.rsplit("/", 1)[-1] or "bin")
    return f"{hashlib.sha1(blob).hexdigest()[:16]}{ext}"


# --------------------------------------------------------------------------- #
# 渲染器
# --------------------------------------------------------------------------- #


class _Renderer:
    """把 ``python-docx`` 的 Document 渲染为 docx-wrapper HTML。"""

    def __init__(
        self,
        document: Document,
        asset_resolver: Optional[ImageResolver] = None,
    ) -> None:
        self.doc = document
        self.part = document.part
        self.styles = self._read_styles()
        # 图片解析策略：返回 src 字符串则输出 <img src>，返回 None 则输出占位。
        self.asset_resolver = asset_resolver
        # rId -> 图片资产（内容寻址命名，天然去重）
        self._assets: dict[str, ImageAsset] = {}
        # rId -> 已生成的 <img> 片段，避免同一图片重复解析与上传
        self._img_cache: dict[str, str] = {}
        self._stats = {"paragraphs": 0, "tables": 0}

    # ---------------------------------------------------------------- styles

    def _read_styles(self) -> dict[str, dict[str, Any]]:
        """收集 ``styles.xml`` 中的样式定义，供 ``basedOn`` 继承链使用。"""
        table: dict[str, dict[str, Any]] = {}
        root = self.doc.styles.element
        if root is None:
            return table
        for style in root.findall(_w("style")):
            style_id = _wval(style, "styleId")
            if not style_id:
                continue
            table[style_id] = {
                "basedOn": _wval(style.find(_w("basedOn")), "val"),
                "pPr": style.find(_w("pPr")),
                "rPr": style.find(_w("rPr")),
            }
        return table

    def _style_chain(self, style_id: Optional[str]) -> list[dict[str, Any]]:
        """返回从根样式到自身的继承链（祖先在前）。"""
        chain: list[dict[str, Any]] = []
        seen: set[str] = set()
        current = style_id
        while current and current in self.styles and current not in seen:
            seen.add(current)
            chain.append(self.styles[current])
            current = self.styles[current]["basedOn"]
        chain.reverse()
        return chain

    # ------------------------------------------------------------- 文档默认值

    def _read_defaults(self) -> dict[str, str]:
        """``docDefaults`` + ``Normal`` 样式 → 默认字体 / 字号 / 段距。

        正向渲染把「文档默认值」抽成 ``section`` 上的 CSS 自定义属性，反向渲染
        时再写回新建文档的 Normal 样式。否则 python-docx 空白模板自带的
        「段后 8pt + 1.08 倍行距 + 11pt 主题字体」会把整篇文档撑开，
        与源文档的版式明显不同。
        """
        root = self.doc.styles.element
        if root is None:
            return {}

        # 优先级由低到高：docDefaults → Normal 样式；逐属性覆盖而非整体替换
        r_sources: list[Any] = []
        p_sources: list[Any] = []
        defaults = root.find(_w("docDefaults"))
        if defaults is not None:
            r_holder = defaults.find(_w("rPrDefault"))
            if r_holder is not None and r_holder.find(_w("rPr")) is not None:
                r_sources.append(r_holder.find(_w("rPr")))
            p_holder = defaults.find(_w("pPrDefault"))
            if p_holder is not None and p_holder.find(_w("pPr")) is not None:
                p_sources.append(p_holder.find(_w("pPr")))
        # 用 python-docx 的样式表取名，避免 styleId 不是 "Normal"（WPS 等会写成 "a"）
        try:
            normal_element = self.doc.styles["Normal"].element
        except KeyError:
            normal_element = None
        if normal_element is not None:
            if normal_element.find(_w("rPr")) is not None:
                r_sources.append(normal_element.find(_w("rPr")))
            if normal_element.find(_w("pPr")) is not None:
                p_sources.append(normal_element.find(_w("pPr")))

        def first_value(sources: list[Any], tag: str, names: tuple[str, ...]) -> Optional[str]:
            """从高优先级往低找，返回第一个有值的属性。"""
            for source in reversed(sources):
                element = source.find(_w(tag))
                if element is None:
                    continue
                for name in names:
                    value = _wval(element, name)
                    if value is not None:
                        return value
            return None

        css: dict[str, str] = {}

        family = first_value(r_sources, "rFonts", ("eastAsia", "ascii", "hAnsi", "cs"))
        if family:
            css["--docx-font"] = family

        size = first_value(r_sources, "sz", ("val",)) or first_value(
            r_sources, "szCs", ("val",)
        )
        if size is not None:
            css["--docx-font-size"] = _half_points(size)

        before = first_value(p_sources, "spacing", ("before",))
        if before is not None:
            css["--docx-space-before"] = _twips(before)
        after = first_value(p_sources, "spacing", ("after",))
        if after is not None:
            css["--docx-space-after"] = _twips(after)

        line = first_value(p_sources, "spacing", ("line",))
        if line is not None and int(line) > 0:
            rule = first_value(p_sources, "spacing", ("lineRule",)) or "auto"
            if rule in ("exact", "atLeast"):
                css["--docx-line-height"] = _twips(line)
            else:
                css["--docx-line-height"] = _num(int(line) / 240)
        return css

    # ------------------------------------------------------------- css 提取

    @staticmethod
    def _run_css(r_pr: Any) -> dict[str, str]:
        """``w:rPr`` → 字符级 CSS。"""
        css: dict[str, str] = {}
        if r_pr is None:
            return css

        fonts = r_pr.find(_w("rFonts"))
        if fonts is not None:
            family = (
                _wval(fonts, "eastAsia")
                or _wval(fonts, "ascii")
                or _wval(fonts, "hAnsi")
                or _wval(fonts, "cs")
            )
            if family:
                css["font-family"] = family

        bold = r_pr.find(_w("b"))
        if bold is not None:
            css["font-weight"] = "normal" if _wval(bold, "val") in ("0", "false", "off") else "bold"

        italic = r_pr.find(_w("i"))
        if italic is not None and _wval(italic, "val") not in ("0", "false", "off"):
            css["font-style"] = "italic"

        size = r_pr.find(_w("sz"))
        if size is None:
            size = r_pr.find(_w("szCs"))
        if size is not None:
            raw = _wval(size, "val")
            if raw is not None:
                # docx-preview 用 min-height 与 font-size 同值表达行高占位
                css["min-height"] = _half_points(raw)
                css["font-size"] = _half_points(raw)

        decorations: list[str] = []
        underline = r_pr.find(_w("u"))
        if underline is not None and _wval(underline, "val", "single") != "none":
            decorations.append("underline")
        strike = r_pr.find(_w("strike"))
        if strike is not None and _wval(strike, "val") not in ("0", "false", "off"):
            decorations.append("line-through")
        if decorations:
            css["text-decoration"] = " ".join(decorations)

        color = r_pr.find(_w("color"))
        if color is not None:
            raw = _wval(color, "val")
            if raw and raw != "auto":
                css["color"] = f"#{raw}"

        vert_align = r_pr.find(_w("vertAlign"))
        if vert_align is not None:
            value = _wval(vert_align, "val")
            if value == "superscript":
                css["vertical-align"] = "super"
            elif value == "subscript":
                css["vertical-align"] = "sub"

        return css

    @staticmethod
    def _para_css(p_pr: Any) -> tuple[dict[str, str], Optional[tuple[str, str]]]:
        """``w:pPr`` → 段落级 CSS 与编号信息 ``(ilvl, numId)``。"""
        css: dict[str, str] = {}
        numbering: Optional[tuple[str, str]] = None
        if p_pr is None:
            return css, numbering

        num_pr = p_pr.find(_w("numPr"))
        if num_pr is not None:
            level = _wval(num_pr.find(_w("ilvl")), "val", "0")
            num_id = _wval(num_pr.find(_w("numId")), "val", "0")
            numbering = (level, num_id)

        jc = p_pr.find(_w("jc"))
        if jc is not None:
            value = _wval(jc, "val")
            if value:
                css["text-align"] = _ALIGN_MAP.get(value, value)

        spacing = p_pr.find(_w("spacing"))
        if spacing is not None:
            before = _wval(spacing, "before")
            if before is not None:
                css["margin-top"] = _twips(before)
            after = _wval(spacing, "after")
            if after is not None:
                css["margin-bottom"] = _twips(after)
            line = _wval(spacing, "line")
            rule = _wval(spacing, "lineRule", "auto")
            if line is not None and int(line) > 0:
                if rule in ("exact", "atLeast"):
                    css["min-height"] = _twips(line)
                    css["line-height"] = _twips(line)
                else:
                    css["line-height"] = _num(int(line) / 240)

        indent = p_pr.find(_w("ind"))
        if indent is not None:
            first_line = _wval(indent, "firstLine")
            hanging = _wval(indent, "hanging")
            if first_line is not None:
                css["text-indent"] = _twips(first_line)
            elif hanging is not None:
                css["text-indent"] = f"-{_twips(hanging)}"
            start = _wval(indent, "start")
            if start is None:
                start = _wval(indent, "left")
            if start is not None:
                css["margin-inline-start"] = _twips(start)

        return css, numbering

    # ------------------------------------------------------- 东亚版式属性

    @staticmethod
    def _ea_paragraph_attrs(p_pr: Any) -> str:
        """``w:spacing`` / ``w:ind`` 里的东亚版式属性 → ``data-docx-p`` 值。"""
        if p_pr is None:
            return ""
        pairs: list[str] = []
        for tag, names in (("spacing", _EA_SPACING_ATTRS), ("ind", _EA_IND_ATTRS)):
            element = p_pr.find(_w(tag))
            if element is None:
                continue
            for name in names:
                value = _wval(element, name)
                if value is not None:
                    pairs.append(f"{name}={value}")
        for tag in _EA_PARA_FLAGS:
            element = p_pr.find(_w(tag))
            if element is None:
                continue
            value = _wval(element, "val")
            pairs.append(tag if value is None else f"{tag}={value}")

        # 段落标记自身的字号：Word/WPS 会用它算最后一行行高，漏掉会让行高偏小
        mark_r_pr = p_pr.find(_w("rPr"))
        if mark_r_pr is not None:
            for tag, key in (("sz", "markSz"), ("szCs", "markSzCs")):
                value = _wval(mark_r_pr.find(_w(tag)), "val")
                if value is not None:
                    pairs.append(f"{key}={value}")
        return ";".join(pairs)

    @staticmethod
    def _ea_run_attrs(r_pr: Any) -> str:
        """``w:rFonts/@hint`` `@cs` 与 ``w:szCs`` → ``data-docx-r`` 值。"""
        if r_pr is None:
            return ""
        pairs: list[str] = []
        fonts = r_pr.find(_w("rFonts"))
        if fonts is not None:
            for name in _EA_RUN_ATTRS:
                value = _wval(fonts, name)
                if value is not None:
                    pairs.append(f"{name}={value}")
        sz_cs = r_pr.find(_w("szCs"))
        value = _wval(sz_cs, "val") if sz_cs is not None else None
        if value is not None:
            pairs.append(f"szCs={value}")
        return ";".join(pairs)

    # ------------------------------------------------------------ 块级渲染

    def render_paragraph(self, p_el: Any) -> str:
        """渲染单个 ``w:p``。"""
        self._stats["paragraphs"] += 1
        p_pr = p_el.find(_w("pPr"))
        style_id = _wval(p_pr.find(_w("pStyle")), "val") if p_pr is not None else None

        css: dict[str, str] = {}
        base_run_css: dict[str, str] = {}
        for style in self._style_chain(style_id):
            if style["pPr"] is not None:
                style_css, _ = self._para_css(style["pPr"])
                css.update(style_css)
            if style["rPr"] is not None:
                base_run_css.update(self._run_css(style["rPr"]))

        numbering: Optional[tuple[str, str]] = None
        if p_pr is not None:
            direct_css, numbering = self._para_css(p_pr)
            css.update(direct_css)

        spans = (self._render_run(run, base_run_css) for run in p_el.iter(_w("r")))
        inner = "".join(span for span in spans if span)

        class_attr = f' class="docx-num-{numbering[0]}-{numbering[1]}"' if numbering else ""
        ea = self._ea_paragraph_attrs(p_pr)
        ea_attr = f' data-docx-p="{html.escape(ea, quote=True)}"' if ea else ""
        return f"<p{class_attr}{ea_attr}{_style_attr(css)}>{inner}</p>"

    def _render_run(self, r_el: Any, base_css: dict[str, str]) -> str:
        """渲染单个 ``w:r``，返回 ``<span>`` 或空串。"""
        css = dict(base_css)
        r_pr = r_el.find(_w("rPr"))
        if r_pr is not None:
            css.update(self._run_css(r_pr))

        pieces: list[str] = []
        has_whitespace = False
        for child in r_el:
            name = _local(child.tag)
            if name == "t":
                text = child.text or ""
                if " " in text or "\t" in text:
                    has_whitespace = True
                pieces.append(html.escape(text, quote=False))
            elif name == "tab":
                pieces.append("\t")
                has_whitespace = True
            elif name == "br":
                # 区分分页符与普通换行，便于 html_to_docx 还原
                if _wval(child, "type") == "page":
                    pieces.append('<br data-break="page">')
                else:
                    pieces.append("<br>")
            elif name == "noBreakHyphen":
                pieces.append("-")
            elif name == "drawing":
                image = self._render_drawing(child)
                if image:
                    pieces.append(image)

        if not pieces:
            return ""

        if has_whitespace:
            css["white-space"] = "pre-wrap"
        ea = self._ea_run_attrs(r_pr)
        ea_attr = f' data-docx-r="{html.escape(ea, quote=True)}"' if ea else ""
        return f"<span{ea_attr}{_style_attr(css)}>{''.join(pieces)}</span>"

    def _render_drawing(self, drawing: Any) -> str:
        """把 ``w:drawing`` 中的图片渲染为 ``<img>``。

        图片本体**不在此处编码**：先构造 :class:`ImageAsset` 交给
        ``asset_resolver``，由它返回 URL / data URI；解析器缺失、返回 ``None``
        或抛异常时统一降级为占位 ``<img data-embed="...">``，保证文档其余部分
        仍能正常渲染。
        """
        blip = drawing.find(".//" + qn("a:blip"))
        if blip is None:
            return ""
        embed = blip.get(qn("r:embed"))
        if not embed:
            return ""
        if embed in self._img_cache:
            return self._img_cache[embed]

        image_part = self.part.related_parts.get(embed)
        if image_part is None:
            return ""

        css: dict[str, str] = {}
        extent = drawing.find(".//" + qn("wp:extent"))
        if extent is not None:
            cx = extent.get("cx")
            cy = extent.get("cy")
            if cx and cy:
                css["width"] = _emu(cx)
                css["height"] = _emu(cy)

        mime = getattr(image_part, "content_type", None) or "image/png"
        asset = ImageAsset(
            r_id=embed,
            name=_asset_name(mime, image_part.blob),
            mime=mime,
            blob=image_part.blob,
            width=css.get("width"),
            height=css.get("height"),
        )
        self._assets.setdefault(embed, asset)

        src: Optional[str] = None
        if self.asset_resolver is not None:
            try:
                src = self.asset_resolver(asset)
            except Exception:  # noqa: BLE001 - 单张图失败不影响整篇渲染
                src = None

        if src:
            rendered = f'<img src="{html.escape(src, quote=True)}"{_style_attr(css)}>'
        else:
            # 占位：保留 rId 与尺寸信息，体积远小于内联 base64
            meta = {"data-embed": embed}
            if asset.width:
                meta["data-width"] = asset.width
            if asset.height:
                meta["data-height"] = asset.height
            attrs = "".join(
                f' {key}="{html.escape(value, quote=True)}"' for key, value in meta.items()
            )
            rendered = f"<img{attrs}{_style_attr(css)}>"

        self._img_cache[embed] = rendered
        return rendered

    # ------------------------------------------------------------ 表格渲染

    def _size_css(self, element: Any, attr_name: str = "w") -> Optional[str]:
        """读取 ``w:tcW`` / ``w:tblW`` 的宽度并转换为 CSS 值。"""
        raw = _wval(element, attr_name)
        if raw is None:
            return None
        value_type = _wval(element, "type", "dxa")
        try:
            number = int(raw)
        except (TypeError, ValueError):
            return None
        if value_type == "pct":
            return f"{_num(number / 50)}%"
        if value_type == "dxa":
            return _twips(number)
        return None

    @staticmethod
    def _borders_css(borders: Any) -> dict[str, str]:
        """``w:tcBorders`` → 边框 CSS（不足的四边按无边框处理）。"""
        widths: list[str] = []
        styles: list[str] = []
        colors: list[str] = []
        for side in ("top", "right", "bottom", "left"):
            element = borders.find(_w(side))
            value = _wval(element, "val", "single") if element is not None else "nil"
            if value in ("nil", "none"):
                widths.append("medium")
                styles.append("none")
                colors.append("currentcolor")
                continue
            size = _wval(element, "sz")
            widths.append(f"{_num(int(size) / 8)}pt" if size is not None else "medium")
            styles.append("solid")
            color = _wval(element, "color", "auto")
            if color in (None, "auto"):
                colors.append("currentcolor")
            elif color.upper() == "000000":
                colors.append("black")
            elif color.upper() == "FFFFFF":
                colors.append("white")
            else:
                colors.append(f"#{color}")
        return {
            "border-width": _pack(widths),
            "border-style": _pack(styles),
            "border-color": _pack(colors),
        }

    @staticmethod
    def _padding_css(margin: Any) -> str:
        """``w:tcMar`` → padding 简写。"""
        values: list[str] = []
        for side in ("top", "right", "bottom", "left"):
            element = margin.find(_w(side))
            raw = _wval(element, "w", "0") if element is not None else "0"
            values.append(_twips(raw))
        return _pack(values)

    def _render_cell(self, tc: Any, info: dict[str, Any]) -> str:
        """渲染单个 ``w:tc``。"""
        attrs = ""
        if info["span"] > 1:
            attrs += f' colspan="{info["span"]}"'
        if info["rowspan"] > 1:
            attrs += f' rowspan="{info["rowspan"]}"'

        css: dict[str, str] = {}
        tc_pr = info["tcPr"]
        if tc_pr is not None:
            tc_w = tc_pr.find(_w("tcW"))
            if tc_w is not None:
                width = self._size_css(tc_w)
                if width is not None:
                    css["width"] = width

            borders = tc_pr.find(_w("tcBorders"))
            if borders is not None:
                css.update(self._borders_css(borders))

            valign = tc_pr.find(_w("vAlign"))
            if valign is not None:
                value = _wval(valign, "val")
                if value:
                    css["vertical-align"] = _VALIGN_MAP.get(value, value)

            margin = tc_pr.find(_w("tcMar"))
            if margin is not None:
                css["padding"] = self._padding_css(margin)

        if info["merge"] == "continue":
            # 被上方 rowspan 占用的延续格：输出占位并由 CSS 隐藏
            css["display"] = "none"

        content: list[str] = []
        for child in tc:
            name = _local(child.tag)
            if name == "p":
                content.append(self.render_paragraph(child))
            elif name == "tbl":
                content.append(self.render_table(child))

        return f"<td{attrs}{_style_attr(css)}>{''.join(content)}</td>"

    def render_table(self, tbl_el: Any) -> str:
        """渲染单个 ``w:tbl``（支持嵌套表格与合并单元格）。"""
        self._stats["tables"] += 1
        table_css: dict[str, str] = {}
        tbl_pr = tbl_el.find(_w("tblPr"))
        if tbl_pr is not None:
            tbl_w = tbl_pr.find(_w("tblW"))
            if tbl_w is not None:
                width = self._size_css(tbl_w)
                if width is not None:
                    table_css["width"] = width
                elif _wval(tbl_w, "type") == "auto":
                    table_css["width"] = "auto"

            layout = tbl_pr.find(_w("tblLayout"))
            table_css["table-layout"] = (
                "fixed" if layout is not None and _wval(layout, "type") == "fixed" else "auto"
            )

            jc = tbl_pr.find(_w("jc"))
            if jc is not None and _wval(jc, "val") in ("center", "centerContinuous"):
                table_css["margin-left"] = "auto"
                table_css["margin-right"] = "auto"
        else:
            table_css["table-layout"] = "auto"

        columns: list[str] = []
        grid = tbl_el.find(_w("tblGrid"))
        if grid is not None:
            for grid_col in grid.findall(_w("gridCol")):
                raw = _wval(grid_col, "w")
                columns.append(_twips(raw) if raw is not None else "auto")
        colgroup = ""
        if columns:
            colgroup = "<colgroup>" + "".join(
                f'<col style="width: {width};">' for width in columns
            ) + "</colgroup>"

        rows, occupied, cells = self._layout_table(tbl_el)
        self._resolve_rowspan(occupied, cells)

        rows_html: list[str] = []
        for tr, row_cells in rows:
            row_css: dict[str, str] = {}
            tr_pr = tr.find(_w("trPr"))
            if tr_pr is not None:
                height = tr_pr.find(_w("trHeight"))
                if height is not None:
                    raw = _wval(height, "val")
                    if raw is not None:
                        row_css["height"] = _twips(raw)
                jc = tr_pr.find(_w("jc"))
                if jc is not None:
                    value = _wval(jc, "val")
                    if value:
                        row_css["text-align"] = _ALIGN_MAP.get(value, value)
            cells_html = "".join(self._render_cell(tc, info) for tc, info in row_cells)
            rows_html.append(f"<tr{_style_attr(row_css)}>{cells_html}</tr>")

        return (
            f"<table{_style_attr(table_css)}>{colgroup}{''.join(rows_html)}</table>"
        )

    @staticmethod
    def _layout_table(
        tbl_el: Any,
    ) -> tuple[list[tuple[Any, list[tuple[Any, dict[str, Any]]]]], dict[tuple[int, int], int], dict[int, dict[str, Any]]]:
        """按逻辑网格解析表格，返回 (行数据, 占位表, 单元格信息表)。"""
        rows: list[tuple[Any, list[tuple[Any, dict[str, Any]]]]] = []
        occupied: dict[tuple[int, int], int] = {}
        cells: dict[int, dict[str, Any]] = {}

        for row_index, tr in enumerate(tbl_el.findall(_w("tr"))):
            column = 0
            row_cells: list[tuple[Any, dict[str, Any]]] = []
            for tc in tr.findall(_w("tc")):
                while (row_index, column) in occupied:
                    column += 1

                tc_pr = tc.find(_w("tcPr"))
                span = 1
                merge: Optional[str] = None
                if tc_pr is not None:
                    grid_span = tc_pr.find(_w("gridSpan"))
                    if grid_span is not None:
                        raw = _wval(grid_span, "val")
                        if raw is not None:
                            span = int(raw)
                    v_merge = tc_pr.find(_w("vMerge"))
                    if v_merge is not None:
                        merge = _wval(v_merge, "val", "continue")

                key = len(cells)
                info = {
                    "tcPr": tc_pr,
                    "row": row_index,
                    "col": column,
                    "span": span,
                    "merge": merge,
                    "rowspan": 1,
                }
                cells[key] = info
                for col in range(column, column + span):
                    occupied[(row_index, col)] = key
                row_cells.append((tc, info))
                column += span
            rows.append((tr, row_cells))

        return rows, occupied, cells

    @staticmethod
    def _resolve_rowspan(
        occupied: dict[tuple[int, int], int], cells: dict[int, dict[str, Any]]
    ) -> None:
        """由 ``vMerge=restart`` 与后续 ``continue`` 格推算 rowspan。"""
        for key, info in cells.items():
            if info["merge"] != "restart":
                continue
            rowspan = 1
            probe = info["row"] + 1
            while True:
                neighbour = occupied.get((probe, info["col"]))
                if neighbour is None or cells[neighbour]["merge"] != "continue":
                    break
                rowspan += 1
                probe += 1
            info["rowspan"] = rowspan

    # -------------------------------------------------------------- 文档级

    @staticmethod
    def _sect_attrs(section: Any) -> str:
        """``sectPr`` 上的文档网格 → ``data-docx-sect``。

        漏掉 ``w:docGrid`` 会让新建文档保留 python-docx 模板自带的 18pt 网格，
        中文文档的行距会被网格吸附。
        """
        sect_pr = getattr(section, "_sectPr", None)
        if sect_pr is None:
            return ""
        grid = sect_pr.find(_w("docGrid"))
        if grid is None:
            return ""
        return "docGrid={},{},{}".format(
            _wval(grid, "type", "") or "",
            _wval(grid, "linePitch", "") or "",
            _wval(grid, "charSpace", "") or "",
        )

    def _article_css(self, section: Any) -> dict[str, str]:
        """``w:cols`` → 分栏 CSS。

        源文档没有 ``w:cols`` 时不写 ``column-gap``：凭空补一个默认值会让
        反向渲染写出源文档里并不存在的 ``w:cols/@w:space``，破坏往返一致性。
        """
        css = {"column-count": "1"}
        sect_pr = getattr(section, "_sectPr", None)
        cols = sect_pr.find(_w("cols")) if sect_pr is not None else None
        if cols is not None:
            num = _wval(cols, "num")
            if num:
                css["column-count"] = num
            space = _wval(cols, "space")
            if space:
                css["column-gap"] = _twips(space)
        return css

    @property
    def assets(self) -> list[ImageAsset]:
        """按出现顺序返回去重后的图片资产。"""
        return list(self._assets.values())

    @property
    def stats(self) -> dict[str, int]:
        """段落 / 表格 / 图片数量统计。"""
        return {**self._stats, "images": len(self._assets)}

    def render_body(self) -> str:
        """按文档顺序渲染正文中的段落与表格。"""
        blocks: list[str] = []
        for child in self.doc.element.body:
            name = _local(child.tag)
            if name == "p":
                blocks.append(self.render_paragraph(child))
            elif name == "tbl":
                blocks.append(self.render_table(child))
        return "".join(block for block in blocks if block)

    def render(self) -> str:
        """渲染整篇文档为 docx-wrapper HTML。"""
        section = self.doc.sections[0]
        padding = " ".join(
            f"{_num(_length_pt(value, 0))}pt"
            for value in (
                section.top_margin,
                section.right_margin,
                section.bottom_margin,
                section.left_margin,
            )
        )
        section_css = {
            "padding": padding,
            "width": f"{_num(_length_pt(section.page_width, 595.3))}pt",
            "min-height": f"{_num(_length_pt(section.page_height, 841.9))}pt",
        }
        # 文档默认值以 CSS 自定义属性带出，供 html_to_docx 还原 Normal 样式
        section_css.update(self._read_defaults())

        sect_attrs = self._sect_attrs(section)
        sect_attr = (
            f' data-docx-sect="{html.escape(sect_attrs, quote=True)}"' if sect_attrs else ""
        )

        return (
            '<div class="docx-wrapper">\n'
            f'  <section class="docx"{sect_attr}{_style_attr(section_css)}>\n'
            f'    <article{_style_attr(self._article_css(section))}>\n'
            f"{self.render_body()}\n"
            "    </article>\n"
            "  </section>\n"
            "</div>"
        )


def render_docx(path: str, *, asset_resolver: Optional[ImageResolver] = None) -> RenderResult:
    """读取 docx 并渲染，返回 HTML、图片资产清单与结构统计。

    ``asset_resolver`` 决定图片的输出形式：返回 URL / data URI 即写入 ``src``；
    返回 ``None`` 或未提供时输出占位 ``<img data-embed="...">``。
    """
    document = Document(path)
    renderer = _Renderer(document, asset_resolver=asset_resolver)
    return RenderResult(html=renderer.render(), assets=renderer.assets, stats=renderer.stats)


def render_docx_to_html(path: str, *, asset_resolver: Optional[ImageResolver] = None) -> str:
    """读取 docx 文件并渲染为 docx-wrapper HTML 字符串。"""
    return render_docx(path, asset_resolver=asset_resolver).html
