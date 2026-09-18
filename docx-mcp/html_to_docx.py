"""docx-wrapper HTML → docx 反向渲染。

与 ``docx_html.py`` 共用同一套内联 CSS 约定，是它的逆过程::

    div.docx-wrapper > section.docx > article > (p | table)
        → python-docx Document → .docx

映射范围（与正向渲染一一对应）：
    - 页面：section 的 padding → 页边距，width / height → 纸张尺寸
    - 段落：text-align / margin-top / margin-bottom / line-height /
      text-indent / margin-inline-start
    - 字符：font-family / font-size / font-weight / font-style /
      text-decoration / color / vertical-align
    - 表格：colgroup 列宽、tr 行高、td 宽度 / 边框 / 内边距 / 垂直对齐、
      colspan 横向合并、rowspan 纵向合并
      （延续格可省略，缺格会自动补全；``display: none`` 的占位格被忽略）
    - 编号：``class="docx-num-{level}-{numId}"`` → 多级十进制编号
    - 图片：``http(s)`` 链接与 ``data:`` URI 会下载/解码后内嵌；
      只有 ``data-embed`` 的占位图会被跳过
"""

from __future__ import annotations

import base64
import copy
import io
import re
from pathlib import Path
from typing import Any, Optional

import requests
from bs4 import BeautifulSoup
from bs4.element import NavigableString, Tag
from docx import Document
from docx.enum.table import (
    WD_CELL_VERTICAL_ALIGNMENT,
    WD_ROW_HEIGHT_RULE,
    WD_TABLE_ALIGNMENT,
)
from docx.enum.text import WD_BREAK
from docx.enum.text import WD_PARAGRAPH_ALIGNMENT as WD_ALIGN
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls, qn
from docx.shared import Pt, RGBColor

__all__ = ["apply_html_replacements", "html_to_docx", "render_html_to_docx"]

_ALIGN_MAP = {
    "left": WD_ALIGN.LEFT,
    "start": WD_ALIGN.LEFT,
    "center": WD_ALIGN.CENTER,
    "centercontinuous": WD_ALIGN.CENTER,
    "right": WD_ALIGN.RIGHT,
    "end": WD_ALIGN.RIGHT,
    "justify": WD_ALIGN.JUSTIFY,
    "both": WD_ALIGN.JUSTIFY,
    "distribute": WD_ALIGN.DISTRIBUTE,
}

_VALIGN_MAP = {
    "top": WD_CELL_VERTICAL_ALIGNMENT.TOP,
    "middle": WD_CELL_VERTICAL_ALIGNMENT.CENTER,
    "center": WD_CELL_VERTICAL_ALIGNMENT.CENTER,
    "bottom": WD_CELL_VERTICAL_ALIGNMENT.BOTTOM,
}

# 行级 ``w:trPr/w:jc`` 需要字面量（python-docx 无对应 API）
_ALIGN_XML = {
    "left": "left",
    "start": "left",
    "center": "center",
    "centercontinuous": "center",
    "right": "right",
    "end": "right",
    "justify": "both",
    "both": "both",
    "distribute": "distribute",
}

_PT_RE = re.compile(r"^(-?\d+(?:\.\d+)?)\s*pt$")
_NUM_CLASS_RE = re.compile(r"docx-num-(\d+)-(\d+)")

# BeautifulSoup（4.14 起）会把「整段都是空白」的文本节点折叠成一个空格，
# 而 Word 的下划线填空正是连续空格。这里在解析前用私用区字符占位，解析后还原。
_WS_TOKEN = "\ue000"
_TAG_SPLIT_RE = re.compile(r"(<[^>]*>)")
_WS_RUN_RE = re.compile(r"[ \t]{2,}")

# CSS 四值时 1/2/3/4 值的展开顺序：top, right, bottom, left
_SIDES = ("top", "right", "bottom", "left")

_BLOCKS = ("p", "table")

# CSS font-family 会被拆写为这几个 rFonts 属性
_EA_FONT_ATTRS = ("ascii", "eastAsia", "hAnsi")

# 段落上的开关类属性（data-docx-p 里以 ``名字`` 或 ``名字=值`` 出现）
_EA_PARA_FLAGS = ("snapToGrid", "adjustRightInd")

# w:pPr 子元素的 schema 顺序，用于把开关元素插到正确位置
_PPR_SEQ = (
    "w:pStyle",
    "w:keepNext",
    "w:keepLines",
    "w:pageBreakBefore",
    "w:framePr",
    "w:widowControl",
    "w:numPr",
    "w:suppressLineNumbers",
    "w:pBdr",
    "w:shd",
    "w:tabs",
    "w:suppressAutoHyphens",
    "w:kinsoku",
    "w:wordWrap",
    "w:overflowPunct",
    "w:topLinePunct",
    "w:autoSpaceDE",
    "w:autoSpaceDN",
    "w:bidi",
    "w:adjustRightInd",
    "w:snapToGrid",
    "w:spacing",
    "w:ind",
    "w:contextualSpacing",
    "w:mirrorIndents",
    "w:suppressOverlap",
    "w:jc",
    "w:textDirection",
    "w:textAlignment",
    "w:textboxTightWrap",
    "w:outlineLvl",
    "w:divId",
    "w:cnfStyle",
    "w:rPr",
    "w:sectPr",
    "w:pPrChange",
)

# w:sectPr 子元素的 schema 顺序
_SECTPR_SEQ = (
    "w:footnotePr",
    "w:endnotePr",
    "w:type",
    "w:pgSz",
    "w:pgMar",
    "w:paperSrc",
    "w:pgBorders",
    "w:lnNumType",
    "w:pgNumType",
    "w:cols",
    "w:formProt",
    "w:vAlign",
    "w:noEndnote",
    "w:titlePg",
    "w:textDirection",
    "w:bidi",
    "w:rtlGutter",
    "w:docGrid",
    "w:printerSettings",
    "w:sectPrChange",
)


def _insert_ordered(parent: Any, element: Any, tag: str, sequence: tuple[str, ...]) -> None:
    """按 schema 顺序把 ``element`` 插到 ``parent`` 里（找不到后继就追加）。"""
    index = sequence.index(tag)
    parent.insert_element_before(element, *sequence[index + 1 :])


# --------------------------------------------------------------------------- #
# CSS 小工具
# --------------------------------------------------------------------------- #


def _protect_whitespace(markup: str) -> str:
    """把标签外的连续空格/制表符替换为占位字符，避免被 BS4 折叠。"""
    parts = _TAG_SPLIT_RE.split(markup)
    # 偶数下标是标签之间的文本，奇数下标是标签本身
    for index in range(0, len(parts), 2):
        parts[index] = _WS_RUN_RE.sub(lambda match: _WS_TOKEN * len(match.group()), parts[index])
    return "".join(parts)


def _restore_whitespace(text: str) -> str:
    """把占位字符还原成空格。"""
    return text.replace(_WS_TOKEN, " ")


def _parse_style(raw: Optional[str]) -> dict[str, str]:
    """把内联 ``style="..."`` 解析为小写键的字典。"""
    style: dict[str, str] = {}
    if not raw:
        return style
    for item in raw.split(";"):
        key, sep, value = item.partition(":")
        if not sep:
            continue
        key = key.strip().lower()
        value = value.strip()
        if key and value:
            style[key] = value
    return style


def _pt(value: Optional[str]) -> Optional[float]:
    """``"12pt"`` → ``12.0``；非 pt 单位返回 ``None``。"""
    if not value:
        return None
    match = _PT_RE.match(value.strip())
    return float(match.group(1)) if match else None


def _expand4(values: list[str]) -> list[str]:
    """按 CSS 简写规则把 1/2/3 值展开为 top, right, bottom, left。"""
    if not values:
        return ["", "", "", ""]
    if len(values) == 1:
        return values * 4
    if len(values) == 2:
        return [values[0], values[1], values[0], values[1]]
    if len(values) == 3:
        return [values[0], values[1], values[2], values[1]]
    return list(values[:4])


def _int(value: Any, default: int) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def _first_family(value: str) -> str:
    """``"方正小标宋简体, serif"`` → ``方正小标宋简体``。"""
    return value.split(",")[0].strip().strip("\"'")


# --------------------------------------------------------------------------- #
# 编号
# --------------------------------------------------------------------------- #


class _Numbering:
    """把 HTML 的 ``docx-num-{level}-{numId}`` 映射为新文档里的编号定义。"""

    def __init__(self, document: Document) -> None:
        self.document = document
        self.part = self._ensure_part()
        self.mapping: dict[str, int] = {}
        self._next_id = self._next_free_id()

    def _next_free_id(self) -> int:
        """避开模板已有的 numId / abstractNumId，避免撞号。"""
        used: list[int] = []
        root = self.part.element
        for tag, name in (("w:num", "numId"), ("w:abstractNum", "abstractNumId")):
            for element in root.findall(qn(tag)):
                value = element.get(qn(f"w:{name}"))
                if value and value.isdigit():
                    used.append(int(value))
        return max(used, default=0) + 1

    def _ensure_part(self) -> Any:
        try:
            return self.document.part.numbering_part
        except (NotImplementedError, KeyError):
            from docx.parts.numbering import NumberingPart

            part = NumberingPart.new()
            self.document.part.relate_to(part, RT.NUMBERING)
            return part

    def _has_num(self, num_id: str) -> bool:
        for element in self.part.element.findall(qn("w:num")):
            if element.get(qn("w:numId")) == num_id:
                return True
        return False

    def ensure(self, source_num_id: str) -> int:
        """为 HTML 中的 numId 分配编号定义。

        以模板为基底时模板里往往已有同名 numId，此时**直接复用**——编号样式
        （如中文的「1、」「（1）」）就藏在 abstractNum 里，重建会退化成西文
        「1.」，WPS/Word 里一眼能看出不同。
        """
        if source_num_id in self.mapping:
            return self.mapping[source_num_id]
        if source_num_id.isdigit() and self._has_num(source_num_id):
            self.mapping[source_num_id] = int(source_num_id)
            return int(source_num_id)
        num_id = self._next_id
        self._append_definition(num_id)
        self.mapping[source_num_id] = num_id
        self._next_id += 1
        return num_id

    def _append_definition(self, num_id: int) -> None:
        """生成 9 级十进制编号定义（``1.`` / ``1.1.`` / ``1.1.1.`` …）。"""
        levels = "".join(
            '<w:lvl w:ilvl="{i}">'
            '<w:start w:val="1"/>'
            '<w:numFmt w:val="decimal"/>'
            '<w:lvlText w:val="{text}"/>'
            '<w:lvlJc w:val="left"/>'
            '<w:pPr><w:ind w:left="{indent}" w:hanging="420"/></w:pPr>'
            "</w:lvl>".format(
                i=index,
                text=".".join(f"%{i + 1}" for i in range(index + 1)) + ".",
                indent=420 * (index + 1),
            )
            for index in range(9)
        )
        abstract = parse_xml(
            f'<w:abstractNum {nsdecls("w")} w:abstractNumId="{num_id}">'
            '<w:multiLevelType w:val="multilevel"/>'
            f"{levels}</w:abstractNum>"
        )
        num = parse_xml(
            f'<w:num {nsdecls("w")} w:numId="{num_id}">'
            f'<w:abstractNumId w:val="{num_id}"/></w:num>'
        )

        root = self.part.element
        first_num = root.find(qn("w:num"))
        if first_num is None:
            root.append(abstract)
        else:
            # numbering.xml 中所有 abstractNum 必须排在 num 之前
            first_num.addprevious(abstract)
        root.append(num)


def _set_numbering(paragraph: Any, level: int, num_id: int) -> None:
    p_pr = paragraph._p.get_or_add_pPr()
    for existing in p_pr.findall(qn("w:numPr")):
        p_pr.remove(existing)
    num_pr = parse_xml(
        f'<w:numPr {nsdecls("w")}>'
        f'<w:ilvl w:val="{level}"/>'
        f'<w:numId w:val="{num_id}"/>'
        "</w:numPr>"
    )
    # pPr 中 numPr 位于 jc / spacing / ind 之前，直接插到最前即可
    p_pr.insert(0, num_pr)


# --------------------------------------------------------------------------- #
# 段落 / 字符
# --------------------------------------------------------------------------- #


def _apply_paragraph_style(paragraph: Any, style: dict[str, str]) -> None:
    paragraph_format = paragraph.paragraph_format

    align = _ALIGN_MAP.get((style.get("text-align") or "").lower())
    # 注意不能写成 ``if align:``：WD_ALIGN.LEFT 的枚举值是 0，会被判为假
    if align is not None:
        paragraph_format.alignment = align

    before = _pt(style.get("margin-top"))
    if before is not None:
        paragraph_format.space_before = Pt(before)
    after = _pt(style.get("margin-bottom"))
    if after is not None:
        paragraph_format.space_after = Pt(after)

    line_height = style.get("line-height")
    if line_height:
        exact = _pt(line_height)
        if exact is not None:
            paragraph_format.line_spacing = Pt(exact)
        else:
            try:
                paragraph_format.line_spacing = float(line_height)
            except ValueError:
                pass

    indent = _pt(style.get("text-indent"))
    if indent is not None:
        # 负值即悬挂缩进
        paragraph_format.first_line_indent = Pt(indent)

    start = _pt(style.get("margin-inline-start")) or _pt(style.get("margin-left"))
    if start is not None:
        paragraph_format.left_indent = Pt(start)


def _apply_run_style(run: Any, style: dict[str, str]) -> None:
    font = run.font

    family = style.get("font-family")
    if family:
        name = _first_family(family)
        if name:
            font.name = name
            r_pr = run._element.get_or_add_rPr()
            r_fonts = r_pr.get_or_add_rFonts()
            r_fonts.set(qn("w:eastAsia"), name)

    size = _pt(style.get("font-size"))
    if size is not None:
        font.size = Pt(size)

    weight = (style.get("font-weight") or "").strip().lower()
    if weight:
        font.bold = weight == "bold" or (weight.isdigit() and int(weight) >= 600)

    font_style = (style.get("font-style") or "").strip().lower()
    if font_style:
        font.italic = font_style == "italic"

    # 只在确有该装饰时才写，避免生成 ``w:strike val="0"`` 之类的冗余属性
    decoration = (style.get("text-decoration") or "").lower()
    if "underline" in decoration:
        font.underline = True
    if "line-through" in decoration:
        font.strike = True

    color = (style.get("color") or "").strip()
    if color.startswith("#") and len(color) == 7:
        font.color.rgb = RGBColor.from_string(color[1:].upper())

    vertical = (style.get("vertical-align") or "").strip().lower()
    if vertical == "super":
        font.superscript = True
    elif vertical == "sub":
        font.subscript = True


def _apply_ea_paragraph_attrs(paragraph: Any, raw: Optional[str]) -> None:
    """写回 ``beforeLines`` / ``firstLineChars`` 等东亚版式属性。

    这些属性在 Word/WPS 里**优先于**同名绝对值（``before``/``after``/
    ``firstLine``）：只写绝对值的话，WPS 会按绝对值排版，与 HTML 显示不一致
    （典型表现是 WPS 里段落更松散、缩进更小）。
    """
    if not raw:
        return
    p_pr = paragraph._p.get_or_add_pPr()
    for item in raw.split(";"):
        key, sep, value = item.partition("=")
        key, value = key.strip(), value.strip()
        if not key:
            continue

        # 开关类元素：整个元素写回（例如 snapToGrid=0 关掉「对齐文档网格」）
        if key in _EA_PARA_FLAGS:
            element = p_pr.find(qn(f"w:{key}"))
            if element is None:
                attrs = f' w:val="{value}"' if sep else ""
                element = parse_xml(f'<w:{key} {nsdecls("w")}{attrs}/>')
                _insert_ordered(p_pr, element, f"w:{key}", _PPR_SEQ)
            elif sep:
                element.set(qn("w:val"), value)
            continue

        # 段落标记字号：写入 pPr/rPr/sz
        if key in ("markSz", "markSzCs") and sep:
            tag = "szCs" if key == "markSzCs" else "sz"
            r_pr = p_pr.find(qn("w:rPr"))
            if r_pr is None:
                r_pr = parse_xml(f'<w:rPr {nsdecls("w")}/>')
                _insert_ordered(p_pr, r_pr, "w:rPr", _PPR_SEQ)
            element = r_pr.find(qn(f"w:{tag}"))
            if element is None:
                element = parse_xml(f'<w:{tag} {nsdecls("w")}/>')
                r_pr.append(element)
            element.set(qn("w:val"), value)
            continue

        if not sep:
            continue
        parent_name = "spacing" if key in ("beforeLines", "afterLines") else "ind"
        element = p_pr.find(qn(f"w:{parent_name}"))
        if element is None:
            getter = getattr(p_pr, f"get_or_add_{parent_name}", None)
            element = getter() if getter is not None else None
        if element is not None:
            element.set(qn(f"w:{key}"), value)


def _apply_section_extras(section: Any, raw: Optional[str]) -> None:
    """``data-docx-sect``：目前承载文档网格 ``w:docGrid``。

    源文档没有网格时必须**移除** python-docx 模板自带的
    ``<w:docGrid w:linePitch="360"/>``：一旦存在网格，默认「对齐文档网格」的段落
    会被吸附到 18pt 的网格上，中文文档行距被撑开，WPS 里尤其明显。
    """
    sect_pr = section._sectPr
    for existing in sect_pr.findall(qn("w:docGrid")):
        sect_pr.remove(existing)

    spec = ""
    for item in (raw or "").split(";"):
        key, sep, value = item.partition("=")
        if sep and key.strip() == "docGrid":
            spec = value.strip()
    if not spec:
        return

    parts = (spec.split(",") + ["", "", ""])[:3]
    attrs = ""
    if parts[0]:
        attrs += f' w:type="{parts[0]}"'
    if parts[1]:
        attrs += f' w:linePitch="{parts[1]}"'
    if parts[2]:
        attrs += f' w:charSpace="{parts[2]}"'
    _insert_ordered(
        sect_pr,
        parse_xml(f'<w:docGrid {nsdecls("w")}{attrs}/>'),
        "w:docGrid",
        _SECTPR_SEQ,
    )


def _apply_ea_run_attrs(run: Any, raw: Optional[str]) -> None:
    """写回 ``w:rFonts`` 的原始字体规格与 ``w:szCs``。

    CSS 只能表达一个 ``font-family``，写回时被拆成 ascii/hAnsi/eastAsia；源文档
    若只设了其中一部分，多出来的属性虽然取值相同，但语义不等价（例如源文档靠
    ``w:hint`` 决定标点用哪套字体）。因此这里以源文档的原始 rFonts 为准：
    只要源文档给出过字体规格，就先清掉 CSS 推断值再逐项写回。
    """
    if not raw:
        return
    pairs: list[tuple[str, str]] = []
    for item in raw.split(";"):
        key, sep, value = item.partition("=")
        if sep and key.strip():
            pairs.append((key.strip(), value.strip()))
    if not pairs:
        return

    r_pr = run._element.get_or_add_rPr()
    # data-docx-r 里只要有任意一个 rFonts 属性，就以源文档的规格为准
    font_pairs = [
        (key, value)
        for key, value in pairs
        if key in _EA_FONT_ATTRS or key in ("cs", "hint")
    ]
    if font_pairs:
        fonts = r_pr.get_or_add_rFonts()
        for name in _EA_FONT_ATTRS:
            fonts.attrib.pop(qn(f"w:{name}"), None)
        for key, value in font_pairs:
            fonts.set(qn(f"w:{key}"), value)

    for key, value in pairs:
        if key == "szCs":
            element = r_pr.find(qn("w:szCs"))
            if element is None:
                element = parse_xml(f'<w:szCs {nsdecls("w")}/>')
                r_pr.append(element)
            element.set(qn("w:val"), value)


def _fetch_image(src: str) -> Optional[bytes]:
    """下载 http(s) 图片或解码 data URI；失败返回 ``None``。"""
    if src.startswith("data:"):
        _, _, payload = src.partition(",")
        try:
            return base64.b64decode(payload)
        except Exception:  # noqa: BLE001 - 损坏的 data URI 直接跳过
            return None
    if src.startswith(("http://", "https://")):
        try:
            response = requests.get(src, timeout=30)
            response.raise_for_status()
            return response.content
        except Exception:  # noqa: BLE001 - 图片不可达时跳过，不影响正文
            return None
    return None


def _add_image(paragraph: Any, tag: Tag, stats: dict[str, int]) -> None:
    src = (tag.get("src") or "").strip()
    if not src:
        # 仅有 data-embed 的占位图：跳过
        return
    blob = _fetch_image(src)
    if not blob:
        return

    style = _parse_style(tag.get("style"))
    kwargs: dict[str, Any] = {}
    width = _pt(style.get("width"))
    height = _pt(style.get("height"))
    if width:
        kwargs["width"] = Pt(width)
    if height:
        kwargs["height"] = Pt(height)

    try:
        paragraph.add_run().add_picture(io.BytesIO(blob), **kwargs)
    except Exception:  # noqa: BLE001 - 不支持的图片格式直接忽略
        return
    stats["images"] += 1


def _append_inline(
    paragraph: Any,
    tag: Tag,
    inherited: dict[str, str],
    stats: dict[str, int],
    ea: str = "",
) -> None:
    """把 ``<span>`` / 文本 / ``<br>`` / ``<img>`` 依次写入段落。

    ``ea`` 是继承下来的 ``data-docx-r``（hint / cs / szCs）。
    """
    preserve = inherited.get("white-space") in ("pre", "pre-wrap", "pre-line")

    for child in tag.children:
        if isinstance(child, NavigableString):
            text = _restore_whitespace(str(child))
            if not preserve:
                text = re.sub(r"\s+", " ", text)
            if not text:
                continue
            run = paragraph.add_run()
            run.text = text
            _apply_run_style(run, inherited)
            _apply_ea_run_attrs(run, ea)
        elif isinstance(child, Tag):
            if child.name == "br":
                run = paragraph.add_run()
                # <br> 常被包在带样式的 <span> 里，需要继承其字体字号
                _apply_run_style(run, inherited)
                _apply_ea_run_attrs(run, ea)
                if child.get("data-break") == "page":
                    run.add_break(WD_BREAK.PAGE)
                else:
                    run.add_break()
            elif child.name == "img":
                _add_image(paragraph, child, stats)
            elif child.name == "span":
                style = {**inherited, **_parse_style(child.get("style"))}
                _append_inline(
                    paragraph, child, style, stats, child.get("data-docx-r") or ea
                )
            else:
                _append_inline(paragraph, child, inherited, stats, ea)


def _fill_paragraph(paragraph: Any, tag: Tag, numbering: _Numbering, stats: dict[str, int]) -> None:
    style = _parse_style(tag.get("style"))
    _apply_paragraph_style(paragraph, style)
    _apply_ea_paragraph_attrs(paragraph, tag.get("data-docx-p"))

    classes = " ".join(tag.get("class") or [])
    match = _NUM_CLASS_RE.search(classes)
    if match:
        _set_numbering(paragraph, int(match.group(1)), numbering.ensure(match.group(2)))

    _append_inline(paragraph, tag, style, stats)
    stats["paragraphs"] += 1


# --------------------------------------------------------------------------- #
# 表格
# --------------------------------------------------------------------------- #


def _child_rows(table_tag: Tag) -> list[Tag]:
    """取出表格的直接行，不深入嵌套表格。"""
    rows: list[Tag] = []
    for child in table_tag.children:
        if not isinstance(child, Tag):
            continue
        if child.name == "tr":
            rows.append(child)
        elif child.name in ("tbody", "thead", "tfoot"):
            rows.extend(node for node in child.children if isinstance(node, Tag) and node.name == "tr")
    return rows


def _layout_rows(table_tag: Tag) -> tuple[list[list[dict[str, Any]]], list[dict[str, Any]], int]:
    """把表格解析为逻辑网格。

    返回 ``(每行待输出的格子, 起始格列表, 总列数)``。``display: none`` 的
    延续格会被忽略，缺格按 ``rowspan`` 自动补全，因此手工编写的 HTML
    （只写 rowspan 不写延续格）同样可以还原。
    """
    rows = _child_rows(table_tag)
    cells: list[dict[str, Any]] = []
    occupied: dict[tuple[int, int], dict[str, Any]] = {}

    for row_index, tr in enumerate(rows):
        column = 0
        for td in tr.children:
            if not isinstance(td, Tag) or td.name not in ("td", "th"):
                continue
            style = _parse_style(td.get("style"))
            if (style.get("display") or "").lower() == "none":
                continue  # 正向渲染输出的占位延续格，交给 rowspan 重建
            while (row_index, column) in occupied:
                column += 1
            info: dict[str, Any] = {
                "cell": td,
                "style": style,
                "row": row_index,
                "col": column,
                "colspan": max(1, _int(td.get("colspan"), 1)),
                "rowspan": max(1, _int(td.get("rowspan"), 1)),
            }
            cells.append(info)
            for dr in range(info["rowspan"]):
                for dc in range(info["colspan"]):
                    occupied[(row_index + dr, column + dc)] = info
            column += info["colspan"]

    total_cols = 0
    for info in cells:
        total_cols = max(total_cols, info["col"] + info["colspan"])
    total_cols = max(total_cols, 1)

    layout: list[list[dict[str, Any]]] = []
    for row_index in range(len(rows)):
        row_out: list[dict[str, Any]] = []
        column = 0
        while column < total_cols:
            owner = occupied.get((row_index, column))
            if owner is None:
                row_out.append({"kind": "empty", "colspan": 1})
                column += 1
            elif owner["row"] == row_index and owner["col"] == column:
                row_out.append({"kind": "cell", "info": owner, "colspan": owner["colspan"]})
                column += owner["colspan"]
            elif owner["col"] == column:
                # 被上方 rowspan 覆盖：输出一次纵向合并延续格
                row_out.append({"kind": "continue", "colspan": owner["colspan"]})
                column += owner["colspan"]
            else:
                column += 1
        layout.append(row_out)

    return layout, cells, total_cols


def _normalize_cell_widths(table: Any, grid_widths: list[Optional[float]]) -> None:
    """把每个 ``tc`` 的 ``w:tcW`` 归一到「它占据的列宽之和」。

    python-docx 的 ``merge`` 生成的纵向延续格不会带 ``tcW``，而 ``_Column.width``
    又会把它写成同列首行的宽度，导致 ``tcW`` 与 ``tblGrid`` 不一致——设置了
    ``tblLayout=fixed`` 时 Word 会参考 ``tcW``，列宽可能因此跑偏。
    """
    for row in table.rows:
        column = 0
        for tc in row._tr.tc_lst:
            span = 1
            tc_pr = tc.tcPr
            if tc_pr is not None:
                grid_span = tc_pr.find(qn("w:gridSpan"))
                if grid_span is not None:
                    span = max(1, _int(grid_span.get(qn("w:val")), 1))

            widths = [w for w in grid_widths[column : column + span] if w]
            column += span
            if not widths:
                continue

            target = parse_xml(
                f'<w:tcW {nsdecls("w")} w:w="{int(round(sum(widths) * 20))}" w:type="dxa"/>'
            )
            tc_pr = tc.get_or_add_tcPr()
            for existing in tc_pr.findall(qn("w:tcW")):
                tc_pr.remove(existing)
            try:
                tc_pr.insert_element_before(
                    target,
                    "w:gridSpan",
                    "w:hMerge",
                    "w:vMerge",
                    "w:tcBorders",
                    "w:shd",
                    "w:noWrap",
                    "w:tcMar",
                    "w:textDirection",
                    "w:tcFitText",
                    "w:vAlign",
                    "w:hideMark",
                )
            except Exception:  # noqa: BLE001 - 退回追加，Word 仍可读
                tc_pr.append(target)


def _merge_regions(cells: list[dict[str, Any]], table: Any) -> None:
    """按 colspan / rowspan 合并矩形区域（区域互不重叠）。"""
    for info in cells:
        if info["colspan"] == 1 and info["rowspan"] == 1:
            continue
        top_left = table.cell(info["row"], info["col"])
        bottom_right = table.cell(
            info["row"] + info["rowspan"] - 1,
            info["col"] + info["colspan"] - 1,
        )
        top_left.merge(bottom_right)


def _apply_cell_borders(cell: Any, style: dict[str, str]) -> None:
    if "border-style" not in style and "border-width" not in style:
        return

    widths = _expand4((style.get("border-width") or "").split())
    kinds = _expand4((style.get("border-style") or "").split())
    colors = _expand4((style.get("border-color") or "").split())

    parts: list[str] = []
    for index, side in enumerate(_SIDES):
        kind = (kinds[index] or "none").lower()
        if kind in ("none", "hidden"):
            parts.append(f'<w:{side} w:val="nil"/>')
            continue
        width = _pt(widths[index]) or 0.5
        size = max(1, int(round(width * 8)))
        color = colors[index].strip()
        # currentcolor 对应 Word 的 auto；写回 auto 才能与正向渲染对称
        color = "auto" if color in ("", "currentcolor") else color.lstrip("#").upper()
        parts.append(
            f'<w:{side} w:val="single" w:sz="{size}" w:space="0" w:color="{color}"/>'
        )

    tc_pr = cell._tc.get_or_add_tcPr()
    for existing in tc_pr.findall(qn("w:tcBorders")):
        tc_pr.remove(existing)
    tc_pr.append(parse_xml(f'<w:tcBorders {nsdecls("w")}>{"".join(parts)}</w:tcBorders>'))


def _set_table_width(table: Any, width_pt: float) -> None:
    """写入 ``w:tblW``：不写的话 Word 按 auto 处理，表格宽度会整个丢掉。"""
    tbl_pr = table._tbl.tblPr
    for existing in tbl_pr.findall(qn("w:tblW")):
        tbl_pr.remove(existing)
    element = parse_xml(
        f'<w:tblW {nsdecls("w")} w:w="{int(round(width_pt * 20))}" w:type="dxa"/>'
    )
    try:
        # tblW 必须排在 jc / tblLayout 等之前
        tbl_pr.insert_element_before(
            element,
            "w:jc",
            "w:tblCellSpacing",
            "w:tblInd",
            "w:tblBorders",
            "w:shd",
            "w:tblLayout",
            "w:tblCellMar",
            "w:tblLook",
        )
    except Exception:  # noqa: BLE001 - 极端情况下退回追加，Word 仍可读
        tbl_pr.append(element)


def _set_row_alignment(row: Any, align: str) -> None:
    """写入 ``w:trPr/w:jc``（python-docx 没有对应 API）。"""
    tr_pr = row._tr.get_or_add_trPr()
    for existing in tr_pr.findall(qn("w:jc")):
        tr_pr.remove(existing)
    tr_pr.append(parse_xml(f'<w:jc {nsdecls("w")} w:val="{align}"/>'))


def _apply_cell_padding(cell: Any, style: dict[str, str]) -> None:
    if "padding" not in style:
        return
    values = _expand4(style["padding"].split())
    parts = []
    for index, side in enumerate(_SIDES):
        margin = _pt(values[index]) or 0
        parts.append(f'<w:{side} w:w="{int(round(margin * 20))}" w:type="dxa"/>')

    tc_pr = cell._tc.get_or_add_tcPr()
    for existing in tc_pr.findall(qn("w:tcMar")):
        tc_pr.remove(existing)
    tc_pr.append(parse_xml(f'<w:tcMar {nsdecls("w")}>{"".join(parts)}</w:tcMar>'))


def _drop_trailing_empty_paragraph(cell: Any) -> None:
    """删除单元格末尾的空段落（嵌套表格后 python-docx 会自动补一个）。"""
    if len(cell.paragraphs) < 1:
        return
    last = cell.paragraphs[-1]
    if last.text or last.runs:
        return
    element = last._element
    if element.getparent() is not None and cell._tc[-1] is element:
        element.getparent().remove(element)


def _fill_cell(cell: Any, td: Tag, numbering: _Numbering, stats: dict[str, int]) -> None:
    """把 ``<td>`` 的子块（段落 / 嵌套表格）写入单元格。"""
    blocks = [
        child
        for child in td.children
        if isinstance(child, Tag) and child.name in ("p", "table")
    ]
    for block in blocks:
        if block.name == "p":
            paragraph = cell.add_paragraph()
            _fill_paragraph(paragraph, block, numbering, stats)
        else:
            _add_table(cell, block, numbering, stats)
            # _Cell.add_table() 会追加一个空段落，若后面还有 <p> 就会多出空行
            _drop_trailing_empty_paragraph(cell)

    # 删除 add_table/add_paragraph 之外遗留的默认空段落
    default = cell.paragraphs[0]
    if len(cell.paragraphs) > 1 and not default.text and not default.runs:
        default._element.getparent().remove(default._element)

    # Word 要求单元格以段落结尾
    last = cell._tc[-1]
    if last.tag == qn("w:tbl"):
        cell.add_paragraph()


def _add_table(container: Any, table_tag: Tag, numbering: _Numbering, stats: dict[str, int]) -> None:
    layout, cells, total_cols = _layout_rows(table_tag)
    if not layout:
        return

    table = container.add_table(rows=len(layout), cols=total_cols)
    table_style = _parse_style(table_tag.get("style"))

    table_width = _pt(table_style.get("width"))
    if table_width:
        table.autofit = False
        _set_table_width(table, table_width)
    elif table_style.get("table-layout") == "fixed":
        table.autofit = False
    if table_style.get("margin-left") == "auto" and table_style.get("margin-right") == "auto":
        table.alignment = WD_TABLE_ALIGNMENT.CENTER

    # 列宽：优先 colgroup，其次首个数据行
    colgroup = table_tag.find("colgroup")
    widths: list[Optional[float]] = []
    if colgroup is not None:
        for col in colgroup.find_all("col"):
            widths.append(_pt(_parse_style(col.get("style")).get("width")))
    for index, width in enumerate(widths[:total_cols]):
        if width:
            table.columns[index].width = Pt(width)

    # 内容写入起始格（被合并吸收的格子保持为空）
    for info in cells:
        cell = table.cell(info["row"], info["col"])
        _fill_cell(cell, info["cell"], numbering, stats)

    _merge_regions(cells, table)
    _normalize_cell_widths(table, widths + [None] * (total_cols - len(widths)))

    # 合并后再套用单元格级样式，避免 tcPr 被 merge 重建
    for info in cells:
        cell = table.cell(info["row"], info["col"])
        style = info["style"]
        width = _pt(style.get("width"))
        if width:
            cell.width = Pt(width)
        _apply_cell_borders(cell, style)
        _apply_cell_padding(cell, style)
        vertical = _VALIGN_MAP.get((style.get("vertical-align") or "").lower())
        if vertical is not None:
            cell.vertical_alignment = vertical

    # 行高与行级对齐（w:trPr/trHeight、w:trPr/jc）
    for index, tr in enumerate(_child_rows(table_tag)):
        if index >= len(table.rows):
            break
        row_style = _parse_style(tr.get("style"))
        row = table.rows[index]
        height = _pt(row_style.get("height"))
        if height:
            row.height = Pt(height)
            # CSS 的表格行高是「最小高度」，写 atLeast 才能与源文档一致
            row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
        row_align = _ALIGN_XML.get((row_style.get("text-align") or "").lower())
        if row_align:
            _set_row_alignment(row, row_align)

    stats["tables"] += 1


# --------------------------------------------------------------------------- #
# 文档级
# --------------------------------------------------------------------------- #


def _apply_document_defaults(document: Document, style: dict[str, str]) -> None:
    """把 ``section`` 上的 ``--docx-*`` 默认值写回 Normal 样式。

    python-docx 的空白模板在 ``docDefaults`` 里自带「段后 8pt + 1.08 倍行距 +
    11pt 主题字体」，而 HTML 只携带**显式**格式，没写间距的段落会继承这些默认值，
    导致整篇被撑开。这里优先用源文档带出来的默认值；HTML 没带（例如手工编写）时
    按「零段距 + 单倍行距」处理，同样能中和空白模板的干扰。
    """
    normal = document.styles["Normal"]

    family = style.get("--docx-font")
    if family:
        normal.font.name = family
        r_pr = normal.element.get_or_add_rPr()
        r_pr.get_or_add_rFonts().set(qn("w:eastAsia"), family)

    size = _pt(style.get("--docx-font-size"))
    if size is not None:
        normal.font.size = Pt(size)

    paragraph_format = normal.paragraph_format
    paragraph_format.space_before = Pt(_pt(style.get("--docx-space-before")) or 0)
    paragraph_format.space_after = Pt(_pt(style.get("--docx-space-after")) or 0)

    line_height = style.get("--docx-line-height")
    if line_height is None:
        paragraph_format.line_spacing = 1.0
    else:
        exact = _pt(line_height)
        if exact is not None:
            paragraph_format.line_spacing = Pt(exact)
        else:
            try:
                paragraph_format.line_spacing = float(line_height)
            except ValueError:
                paragraph_format.line_spacing = 1.0


def _apply_columns(section: Any, article_style: dict[str, str]) -> None:
    """``article`` 的 column-count / column-gap → ``w:cols``。

    python-docx 模板自带 ``<w:cols w:space="720"/>``，与源文档的 425（21.25pt）
    不一致；只在 HTML 明确给出分栏信息时才覆盖，手写 HTML 则保持原样。
    """
    num = article_style.get("column-count")
    gap = _pt(article_style.get("column-gap"))
    if num is None and gap is None:
        return

    sect_pr = section._sectPr
    cols = sect_pr.find(qn("w:cols"))
    if cols is None:
        cols = parse_xml(f'<w:cols {nsdecls("w")}/>')
        try:
            sect_pr.insert_element_before(
                cols,
                "w:formProt",
                "w:vAlign",
                "w:noEndnote",
                "w:titlePg",
                "w:textDirection",
                "w:bidi",
                "w:rtlGutter",
                "w:docGrid",
            )
        except Exception:  # noqa: BLE001 - 退回追加，Word 仍可读
            sect_pr.append(cols)

    if num is not None:
        cols.set(qn("w:num"), str(_int(num, 1)))
    if gap is not None:
        cols.set(qn("w:space"), str(int(round(gap * 20))))


def _apply_page_setup(document: Document, soup: BeautifulSoup) -> dict[str, str]:
    section_tag = soup.select_one("section.docx")
    if section_tag is None:
        return {}
    style = _parse_style(section_tag.get("style"))
    article_tag = soup.select_one("article")
    article_style = _parse_style(article_tag.get("style")) if article_tag is not None else {}
    _apply_columns(document.sections[0], article_style)
    _apply_section_extras(document.sections[0], section_tag.get("data-docx-sect"))
    section = document.sections[0]

    padding = _expand4(style.get("padding", "").split())
    for value, attribute in zip(
        padding, ("top_margin", "right_margin", "bottom_margin", "left_margin")
    ):
        margin = _pt(value)
        if margin is not None:
            setattr(section, attribute, Pt(margin))

    width = _pt(style.get("width")) or _pt(style.get("min-width"))
    if width:
        section.page_width = Pt(width)
    # 正向渲染把纸张高度写进 min-height，这里两种都要认
    height = _pt(style.get("height")) or _pt(style.get("min-height"))
    if height:
        section.page_height = Pt(height)

    return style


def _find_article(soup: BeautifulSoup) -> Tag:
    for selector in (
        "div.docx-wrapper section.docx article",
        "section.docx article",
        "article",
    ):
        node = soup.select_one(selector)
        if node is not None:
            return node
    body = soup.body
    return body if body is not None else soup


def _rewrite_paragraph(soup: BeautifulSoup, paragraph: Tag, new_text: str) -> None:
    """整段改写：沿用该段**最长 run** 的样式，``\\n`` 输出为 ``<br>``。

    取最长 run 而不是第一个，是因为 Word 常把中英文拆成不同字体的 run；
    最长的那个通常代表正文主体字体，合并后字形才不会跑偏。
    """
    spans = paragraph.find_all("span")
    holder = max(spans, key=lambda node: len(node.get_text()), default=None)
    if holder is None:
        holder = paragraph
    holder.clear()
    for index, line in enumerate(new_text.split("\n")):
        if index:
            holder.append(soup.new_tag("br"))
        holder.append(line)
    for extra in spans:
        if extra is not holder:
            extra.decompose()


def apply_html_replacements(
    html_text: str,
    replacements: Optional[list[dict[str, Any]]],
    *,
    order: str = "longest-first",
    dry_run: bool = False,
    mode: str = "single",
) -> tuple[str, dict[str, Any]]:
    """按 AI 给出的 old/new 规则改写 HTML 文本。

    每条规则先按**整段文本**匹配——自动跨 ``<span>`` 合并 Word 的 run 切分，
    命中后保留该段最长 span 的样式整段重写。关键修复：**整段命中之后就不再对
    该段做字面替换**。旧实现在 ① 之后无条件跑 ②，当 ``new`` 包含 ``old``
    （例如 ``"…作品"`` → ``"…作品及其需求规格说明书"``）时会把刚写入的文本
    再替换一遍，导致文本翻倍。

    参数:
        order: ``"longest-first"``（默认）按 ``old`` 长度降序执行，避免短串先
            吞掉长串（原先靠人脑把「3.3.8 迁移用户数据库」排在「迁移用户数据库」
            之前）；``"as-given"`` 保持传入顺序。
        dry_run: 只计算命中位置，不修改 HTML。
        mode: ``"single"``（默认）每个匹配位置只替换一次，天然幂等；
            ``"loop"`` 反复替换直到不再变化，用于 ``--`` → ``-`` 这类收敛式改写。

    返回:
        ``(改写后的 HTML, 结构化报告)``。报告字段：``applied`` 命中条数、
        ``missed`` 未命中的 old 列表、``results`` 每条规则命中的段落索引与上下文、
        ``warnings`` 顺序/包含关系提示。
    """
    prepared: list[dict[str, Any]] = []
    for item in replacements or []:
        old = str(item.get("old") or "")
        if not old:
            continue
        prepared.append(
            {
                "old": old,
                "new": str(item.get("new") or ""),
                "mode": str(item.get("mode") or mode).lower(),
            }
        )

    report: dict[str, Any] = {
        "dryRun": dry_run,
        "order": order,
        "mode": mode,
        "applied": 0,
        "missed": [],
        "results": [],
        "warnings": [],
    }
    if not prepared:
        return html_text, report

    if order == "longest-first":
        prepared.sort(key=lambda item: len(item["old"]), reverse=True)

    for index, item in enumerate(prepared):
        for other_index, other in enumerate(prepared):
            if (
                index != other_index
                and item["old"] != other["old"]
                and other["old"] in item["old"]
            ):
                report["warnings"].append(
                    f"规则顺序提示：{other['old']!r} 是 {item['old']!r} 的子串，"
                    f"已按 longest-first 先处理长串。"
                )
    for item in prepared:
        if item["new"] and item["old"] in item["new"]:
            report["warnings"].append(
                f"规则 {item['old']!r} → {item['new']!r} 的 new 包含 old，"
                f"已强制 single 模式以避免文本翻倍。"
            )
            item["mode"] = "single"

    soup = BeautifulSoup(_protect_whitespace(html_text), "lxml")
    root = soup.select_one("div.docx-wrapper") or soup
    work = copy.deepcopy(root) if dry_run else root

    for item in prepared:
        old = item["old"]
        new = item["new"]
        hits: list[dict[str, Any]] = []

        # ① 整段匹配：同一段文本可能出现在多处（正文、板书、课后拓展…），全部改写
        paragraphs = work.find_all("p")
        for index, node in enumerate(paragraphs):
            plain = node.get_text().replace(_WS_TOKEN, " ")
            if plain != old:
                continue
            hits.append(
                {
                    "kind": "paragraph",
                    "paragraphIndex": index,
                    "before": plain[:30],
                    "hit": old,
                    "after": plain[-30:],
                }
            )
            if not dry_run:
                _rewrite_paragraph(soup, node, new)

        # ② 仅当整段完全没命中时，才退化为文本节点级字面替换
        #    （处理带前后缀、尾部全角空格或嵌在句中的情况）
        if not hits:
            old_token = old.replace(" ", _WS_TOKEN)
            for node in list(work.find_all(string=True)):
                text = str(node)
                if old not in text and old_token not in text:
                    continue
                target_old = old_token if old_token in text else old
                parent = node.find_parent("p")
                hits.append(
                    {
                        "kind": "text",
                        "paragraphIndex": paragraphs.index(parent) if parent in paragraphs else None,
                        "before": text.split(target_old)[0][-30:],
                        "hit": target_old,
                        "after": text.split(target_old)[-1][:30],
                    }
                )
                if not dry_run:
                    if item["mode"] == "loop":
                        replaced = text
                        for _ in range(50):
                            if target_old not in replaced:
                                break
                            replaced = replaced.replace(target_old, new)
                    else:
                        replaced = text.replace(target_old, new)
                    node.replace_with(replaced)

        if hits:
            report["applied"] += 1
        else:
            report["missed"].append(old)
        report["results"].append(
            {
                "old": old,
                "new": new,
                "mode": item["mode"],
                "matched": len(hits),
                "missed": not hits,
                "hits": hits[:50],
            }
        )

    if dry_run:
        return html_text, report
    return str(root).replace(_WS_TOKEN, " "), report


def _clear_body(document: Document) -> None:
    """清空正文，只保留末尾的 ``w:sectPr``（页面设置）。"""
    body = document.element.body
    for child in list(body):
        if child.tag != qn("w:sectPr"):
            body.remove(child)


def render_html_to_docx(
    html: str, output_path: str, template: Optional[str] = None
) -> dict[str, Any]:
    """把 docx-wrapper HTML 还原为 docx 并落盘，返回路径与统计。

    ``template`` 给定时以该文档为基底：正文被清空后按 HTML 重建，而
    ``styles.xml`` / ``settings.xml`` / ``numbering.xml`` / ``fontTable.xml`` /
    页面设置等**全部继承模板**。这是保真的关键——Word/WPS 的排版还受
    ``settings.xml``（标点压缩、兼容模式、默认制表位）和 ``styles.xml``
    影响，HTML 里表达不了，只能从模板带过来。

    不传 ``template`` 时退回空白文档，并用 HTML 里的默认值兜底。
    """
    soup = BeautifulSoup(_protect_whitespace(html), "lxml")
    article = _find_article(soup)

    if template:
        document = Document(template)
        _clear_body(document)
    else:
        document = Document()
        section_style = _apply_page_setup(document, soup)
        _apply_document_defaults(document, section_style)
    numbering = _Numbering(document)

    stats = {"paragraphs": 0, "tables": 0, "images": 0}
    for child in article.children:
        if not isinstance(child, Tag) or child.name not in _BLOCKS:
            continue
        if child.name == "p":
            _fill_paragraph(document.add_paragraph(), child, numbering, stats)
        else:
            _add_table(document, child, numbering, stats)

    target = Path(output_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    document.save(str(target))

    return {
        "docx_path": str(target.resolve()),
        "bytes": target.stat().st_size,
        "stats": stats,
    }


def html_to_docx(html: str, output_path: str) -> str:
    """便捷入口：HTML → docx，返回落盘路径。"""
    return render_html_to_docx(html, output_path)["docx_path"]
