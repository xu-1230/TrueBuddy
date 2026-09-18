"""docx 文档理解与定位编辑。

``docx_html.py`` / ``html_to_docx.py`` 解决的是「模板 → HTML → 改写 → 文档」的
**渲染往返**问题，保真度很高，但不具备对文档本身的**理解**与**定位编辑**能力：
不能搜、不能按位置读、不能按位置改、不能 diff、不能增删段落。本模块补齐这一层。

所有编辑都在 OOXML 层完成——只改 ``w:t`` 文本，不动 ``w:pPr`` / ``w:rPr``，
因此字体、字号、缩进、编号、页面设置与模板**完全一致**，也不需要再走一遍
HTML 往返。

寻址约定（稳定路径）
--------------------
    p:12                正文第 12 个段落（0 起，按文档顺序）
    t:3                 第 3 个表格（含嵌套表格，按文档顺序全局编号）
    t:3/r:2/c:1         表格 t:3 中逻辑网格第 2 行第 1 列的单元格
    t:3/r:2/c:1/p:0     该单元格内的第 0 个段落

``r`` 是**逻辑网格**行号，``c`` 是**逻辑网格**列号：合并单元格以其左上角坐标
参与寻址，被 rowspan / colspan 覆盖的延续格不会单独出现。

``w:t`` 语义
------------
搜索与替换都以 ``w:t`` 文本为准（``w:tab`` / ``w:br`` / 域代码不参与匹配）。
跨 ``w:run`` 的匹配由本模块自动处理：命中后新文本写入**起始 run**，
其格式因此保留——这正是原先把「第3章 / 关系数据库标准语言SQL」拆在三个
``<span>`` 里也能命中的原因，现在它是显式、可预期的行为。
"""

from __future__ import annotations

import copy
import difflib
import re
from dataclasses import dataclass
from typing import Any, Iterable, Optional

from docx import Document
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls, qn

__all__ = [
    "DocModel",
    "ParagraphRef",
    "TableRef",
    "open_document",
    "search",
    "get_text",
    "get_runs",
    "get_tables",
    "describe_outline",
    "replace_rules",
    "diff_documents",
    "set_cell_text",
    "insert_paragraph",
    "delete_paragraph",
    "fill_fields",
    "validate_document",
]

_XML_SPACE = "{http://www.w3.org/XML/1998/namespace}space"
_MAX_LOOP = 500

# 段落格式签名只取这些标签：够判定「版式是否变化」，又不会因 rsid 之类的
# 编辑痕迹噪声而误报。
_PARA_FORMAT_TAGS = ("pStyle", "jc", "ind", "spacing", "numPr", "outlineLvl", "pBdr", "shd")
_RUN_FORMAT_TAGS = ("rFonts", "sz", "szCs", "b", "bCs", "i", "u", "strike", "color", "vertAlign")

# 校验用的占位符特征
_PLACEHOLDER_PATTERNS = (
    (r"_{3,}", "下划线占位"),
    (r"[XxＸｘ]{3,}", "XXX 占位"),
    (r"\{\{[^{}]*\}\}", "{{}} 占位符"),
    (r"\$\{[^{}]*\}", "${} 占位符"),
    (r"【[^】]{0,20}】", "【】 占位符"),
    (r"(?:待填|待补充|待定|TODO|TBD|XXX)", "待填标记"),
)


# --------------------------------------------------------------------------- #
# XML / 文本小工具
# --------------------------------------------------------------------------- #


def _w(tag: str) -> str:
    """``"p"`` → ``"{...}p"``。"""
    return qn(f"w:{tag}")


def _local(tag: Any) -> str:
    """去掉标签的命名空间。"""
    return str(tag).rsplit("}", 1)[-1]


def _attr(element: Any, name: str, default: Optional[str] = None) -> Optional[str]:
    """读取 ``w:{name}`` 属性（元素为空时 lxml 会判假，故一律用 ``is None``）。"""
    if element is None:
        return default
    value = element.get(qn(f"w:{name}"))
    return default if value is None else value


def _set_text(node: Any, text: str) -> None:
    """写入 ``w:t`` 文本，必要时补 ``xml:space="preserve"`` 保住首尾空格。"""
    node.text = text
    if text != text.strip():
        node.set(_XML_SPACE, "preserve")


def _texts(paragraph: Any) -> list[Any]:
    """按文档顺序返回段落内的 ``w:t`` 元素。"""
    return list(paragraph.iter(_w("t")))


def _paragraph_text(paragraph: Any) -> str:
    """段落可见文本（只取 ``w:t``，不含制表符/换行/域代码）。"""
    return "".join(node.text or "" for node in paragraph.iter(_w("t")))


def _norm_attrs(node: Any) -> dict[str, str]:
    """元素属性 → 去命名空间的有序字典，供格式比对。"""
    if node is None:
        return {}
    return {_local(key): value for key, value in sorted(node.attrib.items())}


def _locate(starts: list[int], segments: list[str], pos: int) -> Optional[int]:
    """定位偏移 ``pos`` 落在第几个 ``w:t`` 里（落在空隙时取前一个非空节点）。"""
    last_nonempty: Optional[int] = None
    for index, segment in enumerate(segments):
        if not segment:
            continue
        if starts[index] <= pos < starts[index] + len(segment):
            return index
        if pos >= starts[index] + len(segment):
            last_nonempty = index
    return last_nonempty


def _splice_paragraph(paragraph: Any, start: int, end: int, new_text: str) -> bool:
    """把段落 ``[start, end)`` 区间替换为 ``new_text``。

    区间可以横跨多个 ``w:run``（Word 常把中英文或修订切成不同 run）：
    新文本写入**起始 run**，后续 run 内被覆盖的部分清空，**不被覆盖的前后缀
    原样保留**。格式因此不会丢失。
    """
    texts = _texts(paragraph)
    if not texts:
        return False
    segments = [node.text or "" for node in texts]
    starts: list[int] = []
    total = 0
    for segment in segments:
        starts.append(total)
        total += len(segment)

    start = max(0, min(start, total))
    end = max(start, min(end, total))

    start_index = _locate(starts, segments, start)
    end_index = _locate(starts, segments, max(start, end - 1)) if end > start else start_index
    if start_index is None or end_index is None:
        return False

    start_offset = start - starts[start_index]
    end_offset = end - starts[end_index]

    if start_index == end_index:
        segments[start_index] = (
            segments[start_index][:start_offset] + new_text + segments[start_index][end_offset:]
        )
    else:
        segments[start_index] = segments[start_index][:start_offset] + new_text
        segments[end_index] = segments[end_index][end_offset:]
        for index in range(start_index + 1, end_index):
            segments[index] = ""

    for node, segment in zip(texts, segments):
        _set_text(node, segment)
    return True


def _append_text_to_paragraph(paragraph: Any, text: str) -> None:
    """把 ``text`` 追加到段落末尾（无 ``w:t`` 时新建 run）。"""
    texts = _texts(paragraph)
    if texts:
        last = texts[-1]
        _set_text(last, (last.text or "") + text)
        return
    template_rpr = None
    existing_run = paragraph.find(_w("r"))
    if existing_run is not None:
        r_pr = existing_run.find(_w("rPr"))
        if r_pr is not None:
            template_rpr = copy.deepcopy(r_pr)
    run = parse_xml(f'<w:r {nsdecls("w")}/>')
    if template_rpr is not None:
        run.append(template_rpr)
    node = parse_xml(f'<w:t {nsdecls("w")}/>')
    run.append(node)
    paragraph.append(run)
    _set_text(node, text)


def _template_run_properties(paragraph: Any) -> Any:
    """取段落中第一个有 ``rPr`` 的 run，作为新建 run 的格式模板。"""
    for run in paragraph.findall(_w("r")):
        r_pr = run.find(_w("rPr"))
        if r_pr is not None:
            return copy.deepcopy(r_pr)
    return None


def _clear_paragraph(paragraph: Any, text: str) -> None:
    """清空段落文本后写入 ``text``，保留段落属性与首个 run 的格式。"""
    texts = _texts(paragraph)
    if not texts:
        _append_text_to_paragraph(paragraph, text)
        return
    for node in texts[1:]:
        _set_text(node, "")
    _set_text(texts[0], text)


def _new_paragraph(text: str, template: Any = None) -> Any:
    """新建 ``w:p``；``template`` 给定时继承其 ``pPr`` 与 run 格式。"""
    paragraph = parse_xml(f'<w:p {nsdecls("w")}/>')
    r_pr = None
    if template is not None:
        p_pr = template.find(_w("pPr"))
        if p_pr is not None:
            paragraph.append(copy.deepcopy(p_pr))
        r_pr = _template_run_properties(template)
    run = parse_xml(f'<w:r {nsdecls("w")}/>')
    if r_pr is not None:
        run.append(r_pr)
    node = parse_xml(f'<w:t {nsdecls("w")}/>')
    run.append(node)
    paragraph.append(run)
    _set_text(node, text)
    return paragraph


# --------------------------------------------------------------------------- #
# 结构索引
# --------------------------------------------------------------------------- #


@dataclass
class ParagraphRef:
    """一个可寻址的段落。"""

    path: str
    element: Any
    container: str = "body"
    paragraph_index: Optional[int] = None
    table_index: Optional[int] = None
    row: Optional[int] = None
    col: Optional[int] = None
    cell_path: Optional[str] = None

    @property
    def text(self) -> str:
        return _paragraph_text(self.element)

    def location(self) -> dict[str, Any]:
        return {
            "location": self.path,
            "part": "body" if self.container == "body" else "table",
            "paragraphIndex": self.paragraph_index,
            "tableIndex": self.table_index,
            "row": self.row,
            "col": self.col,
        }


@dataclass
class TableRef:
    """一个可寻址的表格（含其逻辑网格单元格）。"""

    path: str
    index: int
    element: Any
    cells: list[dict[str, Any]]


def _table_cells(table_el: Any) -> list[dict[str, Any]]:
    """解析表格为逻辑网格，返回起始单元格列表。

    每个元素形如 ``{tc, row, col, span, rowspan}``；被 rowspan 覆盖的
    ``vMerge=continue`` 延续格不单独返回（它们已并入上方起始格的 rowspan）。
    """
    cell_at: dict[tuple[int, int], int] = {}
    items: list[dict[str, Any]] = []

    for row_index, tr in enumerate(table_el.findall(_w("tr"))):
        column = 0
        for tc in tr.findall(_w("tc")):
            while (row_index, column) in cell_at:
                column += 1
            tc_pr = tc.find(_w("tcPr"))
            span = 1
            merge: Optional[str] = None
            if tc_pr is not None:
                grid_span = tc_pr.find(_w("gridSpan"))
                if grid_span is not None:
                    try:
                        span = max(1, int(_attr(grid_span, "val", "1") or "1"))
                    except ValueError:
                        span = 1
                v_merge = tc_pr.find(_w("vMerge"))
                if v_merge is not None:
                    merge = _attr(v_merge, "val", "continue")
            for offset in range(span):
                cell_at[(row_index, column + offset)] = len(items)
            items.append(
                {
                    "tc": tc,
                    "row": row_index,
                    "col": column,
                    "span": span,
                    "vmerge": merge,
                    "rowspan": 1,
                    "table": None,
                }
            )
            column += span

    for index, item in enumerate(items):
        if item["vmerge"] == "continue":
            continue
        rowspan = 1
        probe = item["row"] + 1
        while True:
            owner = cell_at.get((probe, item["col"]))
            if owner is None:
                break
            candidate = items[owner]
            if candidate["vmerge"] != "continue" or candidate["col"] != item["col"]:
                break
            rowspan += 1
            probe += 1
        item["rowspan"] = rowspan

    return [item for item in items if item["vmerge"] != "continue"]


class DocModel:
    """把 docx 解析成可寻址结构：正文段落 + 表格单元格 + 单元格内段落。"""

    def __init__(self, document: Document) -> None:
        self.document = document
        self.body = document.element.body
        self.paragraphs: list[ParagraphRef] = []
        self.tables: list[TableRef] = []
        self.paragraphs_all: list[ParagraphRef] = []
        self.blocks: list[dict[str, Any]] = []
        self._build()

    # ------------------------------------------------------------- 构建索引

    def _build(self) -> None:
        for child in list(self.body):
            name = _local(child.tag)
            if name == "p":
                ref = ParagraphRef(
                    path=f"p:{len(self.paragraphs)}",
                    element=child,
                    container="body",
                    paragraph_index=len(self.paragraphs),
                )
                self.paragraphs.append(ref)
                self.paragraphs_all.append(ref)
                self.blocks.append({"kind": "paragraph", "ref": ref})
            elif name == "tbl":
                ref = self._index_table(child)
                self.blocks.append({"kind": "table", "ref": ref})

    def _index_table(self, table_el: Any) -> TableRef:
        index = len(self.tables)
        items = _table_cells(table_el)
        table_ref = TableRef(path=f"t:{index}", index=index, element=table_el, cells=items)
        self.tables.append(table_ref)

        for item in items:
            cell_path = f"t:{index}/r:{item['row']}/c:{item['col']}"
            item["path"] = cell_path
            item["table"] = index
            item["paragraphs"] = []
            paragraph_index = 0
            for child in item["tc"]:
                name = _local(child.tag)
                if name == "p":
                    ref = ParagraphRef(
                        path=f"{cell_path}/p:{paragraph_index}",
                        element=child,
                        container="cell",
                        table_index=index,
                        row=item["row"],
                        col=item["col"],
                        cell_path=cell_path,
                    )
                    paragraph_index += 1
                    item["paragraphs"].append(ref)
                    self.paragraphs_all.append(ref)
                elif name == "tbl":
                    self._index_table(child)
        return table_ref

    # ------------------------------------------------------------- 寻址

    def paragraph(self, location: str) -> Optional[ParagraphRef]:
        """按路径解析段落；纯数字视为正文段号。"""
        location = str(location).strip()
        if location.isdigit():
            index = int(location)
            return self.paragraphs[index] if 0 <= index < len(self.paragraphs) else None
        if location.startswith("paragraph:"):
            return self.paragraph(location.split(":", 1)[1])
        for ref in self.paragraphs_all:
            if ref.path == location:
                return ref
        return None

    def cell_at(self, location: str) -> Optional[dict[str, Any]]:
        """按 ``t:0/r:1/c:2`` 解析单元格。"""
        for table in self.tables:
            for item in table.cells:
                if item["path"] == location:
                    return item
        return None

    def find_cell(self, table: int, row: int, col: int) -> Optional[dict[str, Any]]:
        """按表号 + 逻辑行列号解析单元格。"""
        if not 0 <= table < len(self.tables):
            return None
        for item in self.tables[table].cells:
            if item["row"] == row and item["col"] == col:
                return item
        return None

    def table_grid(self, table_index: int) -> list[list[str]]:
        """表格的逻辑网格文本（合并覆盖的格子输出空串）。"""
        table = self.tables[table_index]
        rows = 0
        cols = 0
        for item in table.cells:
            rows = max(rows, item["row"] + item["rowspan"])
            cols = max(cols, item["col"] + item["span"])
        grid = [["" for _ in range(cols)] for _ in range(rows)]
        for item in table.cells:
            text = "".join(ref.text for ref in item.get("paragraphs", [])).strip()
            grid[item["row"]][item["col"]] = text
        return grid

    # ------------------------------------------------------------- 重建

    def rebuild(self) -> "DocModel":
        """重新索引。

        纯文本替换不会改变段落数量，索引始终有效；但 ``insert_paragraph`` /
        ``delete_paragraph`` 会改变文档结构，之后必须重建（或重新打开文档），
        否则旧的路径会指向已经移位的段落。
        """
        self.paragraphs.clear()
        self.tables.clear()
        self.paragraphs_all.clear()
        self.blocks.clear()
        self._build()
        return self

    # ------------------------------------------------------------- 统计

    def structure(self) -> dict[str, int]:
        return {
            "paragraphs": len(self.paragraphs),
            "paragraphs_all": len(self.paragraphs_all),
            "tables": len(self.tables),
            "cells": sum(len(table.cells) for table in self.tables),
        }


def open_document(path: str) -> tuple[Document, DocModel]:
    """打开文档并建立索引。"""
    document = Document(path)
    return document, DocModel(document)


def save_document(document: Any, path: str) -> None:
    """落盘。``document`` 可以是 ``Document`` 或 :class:`DocModel`。

    因为只改 ``w:t``，其余 part（styles.xml / settings.xml / numbering.xml /
    fontTable.xml …）原样保留，格式与模板完全一致。
    """
    if isinstance(document, DocModel):
        document = document.document
    document.save(path)


# --------------------------------------------------------------------------- #
# 作用域解析
# --------------------------------------------------------------------------- #


def _scope_paragraphs(model: DocModel, scope: Optional[dict[str, Any]]) -> list[ParagraphRef]:
    """把 scope 描述解析为候选段落列表。"""
    scope = dict(scope or {})

    if scope.get("location"):
        ref = model.paragraph(str(scope["location"]))
        return [ref] if ref else []

    if scope.get("paragraph") is not None:
        ref = model.paragraph(str(scope["paragraph"]))
        return [ref] if ref else []

    refs = model.paragraphs_all
    part = scope.get("part")
    if part == "body":
        refs = list(model.paragraphs)
    elif part == "table":
        refs = [ref for ref in model.paragraphs_all if ref.container == "cell"]

    if scope.get("table") is not None:
        try:
            table_index = int(scope["table"])
        except (TypeError, ValueError):
            return []
        if not 0 <= table_index < len(model.tables):
            return []
        cells = list(model.tables[table_index].cells)
        if scope.get("row") is not None:
            cells = [cell for cell in cells if cell["row"] == int(scope["row"])]
        column = scope.get("col", scope.get("cell"))
        if column is not None:
            cells = [cell for cell in cells if cell["col"] == int(column)]
        return [ref for cell in cells for ref in cell.get("paragraphs", [])]

    return list(refs)


def _resolve_windows(
    model: DocModel,
    scope: Optional[dict[str, Any]],
    old: str,
    regex: bool,
    ignore_case: bool,
) -> list[tuple[ParagraphRef, int, int]]:
    """解析出「段落 + 段内窗口」列表，``after`` / ``before`` 在此落地。"""
    refs = _scope_paragraphs(model, scope)
    scope = dict(scope or {})
    anchor_after = scope.get("after")
    anchor_before = scope.get("before")

    if anchor_after:
        anchor = str(anchor_after)
        for ref in refs:
            text = ref.text
            position = text.find(anchor)
            if position >= 0:
                return [(ref, position + len(anchor), len(text))]
        return []

    if anchor_before:
        anchor = str(anchor_before)
        for ref in refs:
            text = ref.text
            position = text.find(anchor)
            if position >= 0:
                return [(ref, 0, position)]
        return []

    return [(ref, 0, len(ref.text)) for ref in refs]


# --------------------------------------------------------------------------- #
# 检索
# --------------------------------------------------------------------------- #


def search(
    model: DocModel,
    pattern: str,
    *,
    regex: bool = False,
    ignore_case: bool = False,
    scope: Optional[dict[str, Any]] = None,
    context_chars: int = 40,
    limit: int = 0,
) -> dict[str, Any]:
    """在文档内检索，返回每处命中的**位置 + 上下文**。

    这是原先只能靠 PowerShell 正则 + ``IndexOf`` 兜底的第一步，现在带
    ``part / paragraphIndex / tableIndex / row / col / charOffset`` 结构信息。
    """
    if not pattern:
        raise ValueError("pattern 不能为空")
    flags = re.IGNORECASE if ignore_case else 0
    compiled = re.compile(pattern if regex else re.escape(pattern), flags)

    windows = _resolve_windows(model, scope, pattern, regex, ignore_case)
    hits: list[dict[str, Any]] = []
    for ref, begin, end in windows:
        text = ref.text
        if not text:
            continue
        for match in compiled.finditer(text, begin, end):
            start, stop = match.span()
            if start == stop:
                continue
            hits.append(
                {
                    **ref.location(),
                    "charOffset": start,
                    "hit": text[start:stop],
                    "before": text[max(begin, start - context_chars) : start],
                    "after": text[stop : stop + context_chars],
                    "text": text,
                }
            )
            if limit and len(hits) >= limit:
                return {"pattern": pattern, "count": len(hits), "truncated": True, "hits": hits}
    return {"pattern": pattern, "count": len(hits), "truncated": False, "hits": hits}


# --------------------------------------------------------------------------- #
# 定位读取
# --------------------------------------------------------------------------- #


def get_text(
    model: DocModel,
    *,
    start: int = 0,
    end: Optional[int] = None,
    page: Optional[int] = None,
    page_size: int = 50,
    with_index: bool = True,
    with_table_grid: bool = True,
) -> dict[str, Any]:
    """带稳定索引的纯文本导出（正文段落 + 表格单元格路径），支持分页。

    取代原先「整份 HTML 落盘 → 剥标签 → 手工编号」的做法：既省 token，
    也不会在中途被截断。
    """
    blocks: list[dict[str, Any]] = []
    for index, block in enumerate(model.blocks):
        if block["kind"] == "paragraph":
            ref: ParagraphRef = block["ref"]
            blocks.append(
                {
                    "blockIndex": index,
                    "kind": "paragraph",
                    "location": ref.path,
                    "paragraphIndex": ref.paragraph_index,
                    "text": ref.text,
                }
            )
        else:
            table: TableRef = block["ref"]
            entry: dict[str, Any] = {
                "blockIndex": index,
                "kind": "table",
                "location": table.path,
                "tableIndex": table.index,
                "rowCount": len(model.table_grid(table.index)),
                "colCount": len(model.table_grid(table.index)[0]) if model.table_grid(table.index) else 0,
                "cells": [
                    {
                        "location": item["path"],
                        "row": item["row"],
                        "col": item["col"],
                        "rowspan": item["rowspan"],
                        "colspan": item["span"],
                        "text": "".join(ref.text for ref in item.get("paragraphs", [])),
                    }
                    for item in table.cells
                ],
            }
            if with_table_grid:
                entry["grid"] = model.table_grid(table.index)
            blocks.append(entry)

    total = len(blocks)
    if page is not None:
        page = max(1, int(page))
        slice_start = (page - 1) * page_size
        slice_end = slice_start + page_size
    else:
        slice_start = max(0, int(start))
        slice_end = total if end is None else max(slice_start, int(end))

    window = blocks[slice_start:slice_end]
    if not with_index:
        for block in window:
            block.pop("blockIndex", None)

    pages = max(1, (total + page_size - 1) // page_size)
    return {
        "totalBlocks": total,
        "page": page,
        "pageSize": page_size,
        "pages": pages,
        "from": slice_start,
        "to": min(slice_end, total),
        "structure": model.structure(),
        "blocks": window,
    }


def get_runs(model: DocModel, location: str) -> dict[str, Any]:
    """返回某段落的 run 边界：几段文本、各自格式、能否跨 run 命中。

    这正是原先需要「按 ``<[^>]*>`` 切 text node 数 run 数」才能探测的信息。
    """
    ref = model.paragraph(location)
    if ref is None:
        raise ValueError(f"找不到段落：{location}")
    runs: list[dict[str, Any]] = []
    for index, run in enumerate(ref.element.findall(_w("r"))):
        text = "".join(node.text or "" for node in run.iter(_w("t")))
        if not text:
            continue
        runs.append(
            {
                "runIndex": index,
                "text": text,
                "charStart": sum(len(item["text"]) for item in runs),
                "rPr": _norm_attrs(run.find(_w("rPr"))),
            }
        )
    return {
        "location": ref.path,
        "paragraphIndex": ref.paragraph_index,
        "tableIndex": ref.table_index,
        "row": ref.row,
        "col": ref.col,
        "text": ref.text,
        "runCount": len(runs),
        "splitAcrossRuns": len(runs) > 1,
        "matchAcrossRuns": True,
        "runs": runs,
    }


def get_tables(model: DocModel) -> dict[str, Any]:
    """表格结构查询：行列数、合并信息、单元格路径 → 文本。"""
    tables: list[dict[str, Any]] = []
    for table in model.tables:
        grid = model.table_grid(table.index)
        tables.append(
            {
                "tableIndex": table.index,
                "location": table.path,
                "rowCount": len(grid),
                "colCount": len(grid[0]) if grid else 0,
                "grid": grid,
                "cells": [
                    {
                        "location": item["path"],
                        "row": item["row"],
                        "col": item["col"],
                        "rowspan": item["rowspan"],
                        "colspan": item["span"],
                        "merged": item["rowspan"] > 1 or item["span"] > 1,
                        "text": "".join(ref.text for ref in item.get("paragraphs", [])),
                        "paragraphs": [
                            {"location": ref.path, "text": ref.text}
                            for ref in item.get("paragraphs", [])
                        ],
                    }
                    for item in table.cells
                ],
            }
        )
    return {"count": len(tables), "tables": tables}


def _section_info(section: Any) -> dict[str, Any]:
    """页面设置摘要。"""

    def _pt(length: Any) -> Optional[float]:
        try:
            return round(float(length.pt), 2)
        except Exception:  # noqa: BLE001 - Length 缺失时忽略
            return None

    sect_pr = getattr(section, "_sectPr", None)
    grid = sect_pr.find(_w("docGrid")) if sect_pr is not None else None
    cols = sect_pr.find(_w("cols")) if sect_pr is not None else None
    return {
        "pageWidthPt": _pt(section.page_width),
        "pageHeightPt": _pt(section.page_height),
        "marginsPt": {
            "top": _pt(section.top_margin),
            "right": _pt(section.right_margin),
            "bottom": _pt(section.bottom_margin),
            "left": _pt(section.left_margin),
        },
        "columns": _attr(cols, "num", "1") if cols is not None else "1",
        "docGrid": _norm_attrs(grid) if grid is not None else None,
    }


_LABEL_RE = re.compile(r"^[^：:]{2,16}[：:]\s*(.*)$")
_HEADING_RE = re.compile(r"^\s*(第\s*[一二三四五六七八九十百零〇0-9]+\s*[章节课篇]|\d+(\.\d+)+)")


def describe_outline(model: DocModel) -> dict[str, Any]:
    """一次拿到文档骨架：结构统计 + 页面设置 + 疑似标题 + 疑似封面字段 + 表格概览。"""
    labels: list[dict[str, Any]] = []
    headings: list[dict[str, Any]] = []
    for ref in model.paragraphs:
        text = ref.text.strip()
        if not text:
            continue
        p_pr = ref.element.find(_w("pPr"))
        style = _attr(p_pr.find(_w("pStyle")), "val") if p_pr is not None else None
        if _HEADING_RE.match(text) or (style and any(key in style for key in ("Heading", "标题", "heading"))):
            headings.append({"location": ref.path, "text": text[:60], "style": style})
            continue
        match = _LABEL_RE.match(text)
        if match:
            labels.append(
                {
                    "location": ref.path,
                    "label": text.split("：", 1)[0].split(":", 1)[0],
                    "value": match.group(1).strip(),
                    "text": text,
                }
            )

    tables = []
    for table in model.tables:
        grid = model.table_grid(table.index)
        tables.append(
            {
                "tableIndex": table.index,
                "location": table.path,
                "rowCount": len(grid),
                "colCount": len(grid[0]) if grid else 0,
                "firstRow": grid[0] if grid else [],
                "firstColumn": [row[0] for row in grid if row],
            }
        )

    return {
        "structure": model.structure(),
        "sections": [_section_info(section) for section in model.document.sections],
        "headings": headings,
        "labels": labels,
        "tables": tables,
    }


# --------------------------------------------------------------------------- #
# 替换
# --------------------------------------------------------------------------- #


def _apply_rule_to_ref(
    ref: ParagraphRef,
    rule: dict[str, Any],
    counter: list[int],
    out: list[dict[str, Any]],
    dry_run: bool,
    window: tuple[int, int],
) -> None:
    """在单个段落内执行一条替换规则。

    ``single`` 模式下每次替换后从**新文本之后**继续搜索，因此 ``new`` 包含
    ``old`` 也不会把文本翻倍（这正是旧实现踩过的坑）。
    """
    old: str = rule["old"]
    new: str = rule["new"]
    flags = re.IGNORECASE if rule["ignore_case"] else 0
    compiled = re.compile(old if rule["regex"] else re.escape(old), flags)
    mode: str = rule["mode"]
    occurrence = rule["scope"].get("occurrence")
    occurrence = int(occurrence) if occurrence is not None else None

    begin, end = window
    position = begin
    guard = 0
    while guard < _MAX_LOOP:
        guard += 1
        text = ref.text
        if not text:
            return
        search_from = max(position, begin)
        if search_from >= end:
            return
        match = compiled.search(text, search_from, end)
        if match is None:
            return
        start, stop = match.span()
        if start == stop:
            position = stop + 1
            continue

        counter[0] += 1
        order = counter[0]
        if occurrence is not None and order < occurrence:
            position = stop
            continue
        if occurrence is not None and order > occurrence:
            return

        replacement = match.expand(new) if rule["regex"] else new
        out.append(
            {
                **ref.location(),
                "charOffset": start,
                "hit": text[start:stop],
                "before": text[max(begin, start - 30) : start],
                "after": text[stop : stop + 30],
                "new": replacement,
            }
        )
        if not dry_run:
            _splice_paragraph(ref.element, start, stop, replacement)
        if occurrence is not None:
            return
        position = start if mode == "loop" else start + len(replacement)
    return


def _close_matches(model: DocModel, old: str, top: int = 3) -> list[dict[str, Any]]:
    """为未命中的规则找最相近的段落文本，作为「建议位置」。"""
    texts = []
    for ref in model.paragraphs_all:
        text = ref.text
        if not text:
            continue
        texts.append((ref, text))
    candidates = [text for _, text in texts]
    found = difflib.get_close_matches(old, candidates, n=top, cutoff=0.4)
    suggestions: list[dict[str, Any]] = []
    for item in found:
        for ref, text in texts:
            if text == item:
                suggestions.append({**ref.location(), "text": text[:120]})
                break
    return suggestions


def replace_rules(
    model: DocModel,
    rules: Iterable[dict[str, Any]],
    *,
    order: str = "longest-first",
    dry_run: bool = False,
) -> dict[str, Any]:
    """带作用域的批量替换，返回**结构化变更报告**。

    与 HTML 层 ``replacements`` 的关键差别：
    - 每条规则的每个命中都带位置（``location`` / ``charOffset``）；
    - ``scope`` 可以把「同一文本出现在多个语义不同位置」区分开；
    - ``dryRun`` 先看命中位置，确认无歧义再落盘；
    - ``order="longest-first"`` 自动把长串排在短串之前，不必人脑排序。
    """
    prepared: list[dict[str, Any]] = []
    for raw in rules or []:
        old = str(raw.get("old") or "")
        if not old:
            continue
        prepared.append(
            {
                "old": old,
                "new": str(raw.get("new") or ""),
                "scope": raw.get("scope") or {},
                "regex": bool(raw.get("regex")),
                "ignore_case": bool(raw.get("ignore_case")),
                "mode": str(raw.get("mode") or "single").lower(),
            }
        )

    if not prepared:
        return {
            "dryRun": dry_run,
            "order": order,
            "appliedRules": 0,
            "missedRules": [],
            "results": [],
            "warnings": [],
        }

    warnings: list[str] = []
    if order == "longest-first":
        prepared.sort(key=lambda item: len(item["old"]), reverse=True)

    for index, item in enumerate(prepared):
        for other_index, other in enumerate(prepared):
            if index == other_index:
                continue
            if item["old"] != other["old"] and other["old"] in item["old"]:
                warnings.append(
                    f"规则顺序提示：{other['old']!r} 是 {item['old']!r} 的子串，"
                    f"已按 longest-first 先处理长串。"
                )

    for item in prepared:
        if item["new"] and item["old"] in item["new"]:
            warnings.append(
                f"规则 {item['old']!r} → {item['new']!r} 的 new 包含 old，"
                f"已强制 single 模式以避免文本翻倍。"
            )
            item["mode"] = "single"

    results: list[dict[str, Any]] = []
    applied_rules = 0
    missed_rules: list[dict[str, Any]] = []

    for item in prepared:
        windows = _resolve_windows(
            model, item["scope"], item["old"], item["regex"], item["ignore_case"]
        )
        counter = [0]
        occurrences: list[dict[str, Any]] = []
        for ref, begin, end in windows:
            _apply_rule_to_ref(ref, item, counter, occurrences, dry_run, (begin, end))

        entry: dict[str, Any] = {
            "old": item["old"],
            "new": item["new"],
            "scope": item["scope"],
            "mode": item["mode"],
            "matched": len(occurrences),
            "occurrences": occurrences,
            "missed": not occurrences,
        }
        if occurrences:
            applied_rules += 1
        else:
            suggestions = _close_matches(model, item["old"])
            entry["closest"] = suggestions
            entry["suggestion"] = (
                f"未命中；最相近的段落是 {suggestions[0]['location']}"
                if suggestions
                else "未命中；文档中没有相近文本，请用 docx_search 确认原文"
            )
            missed_rules.append({"old": item["old"], "closest": suggestions})
        results.append(entry)

    return {
        "dryRun": dry_run,
        "order": order,
        "appliedRules": applied_rules,
        "missedRules": missed_rules,
        "results": results,
        "warnings": warnings,
    }


# --------------------------------------------------------------------------- #
# 差异比对
# --------------------------------------------------------------------------- #


def _para_signature(paragraph: Any) -> dict[str, Any]:
    """段落格式签名：只看会影响版式的属性，忽略 rsid 之类的编辑痕迹。

    run 数量本身**不是**版式：Word 会把同一字体的一句话切成多个 run，
    而 HTML 往返后又可能合并成一个。这里把相邻的同格式 run 压缩掉，
    比较的是「格式分段模式」而不是 run 个数，避免把视觉等价的段落判为变化。
    """
    p_pr = paragraph.find(_w("pPr"))
    signature: dict[str, Any] = {}
    if p_pr is not None:
        for tag in _PARA_FORMAT_TAGS:
            node = p_pr.find(_w(tag))
            if node is not None:
                signature[tag] = _norm_attrs(node)

    runs: list[dict[str, Any]] = []
    for run in paragraph.findall(_w("r")):
        if not "".join(node.text or "" for node in run.iter(_w("t"))):
            continue  # 空 run 不参与版式比较
        r_pr = run.find(_w("rPr"))
        item: dict[str, Any] = {}
        if r_pr is not None:
            for tag in _RUN_FORMAT_TAGS:
                node = r_pr.find(_w(tag))
                if node is not None:
                    item[tag] = _norm_attrs(node)
        if runs and runs[-1] == item:
            continue  # 相邻同格式：合并
        runs.append(item)
    signature["runs"] = runs
    return signature


def diff_documents(path_a: str, path_b: str, level: str = "text") -> dict[str, Any]:
    """证明「只改了文字、版式零变化」。

    ``level="text"`` 只比文本；``level="format"`` 额外比段落/表格数量、
    页面设置与逐段格式签名。
    """
    document_a = Document(path_a)
    document_b = Document(path_b)
    model_a = DocModel(document_a)
    model_b = DocModel(document_b)

    refs_a = model_a.paragraphs_all
    refs_b = model_b.paragraphs_all
    texts_a = [ref.text for ref in refs_a]
    texts_b = [ref.text for ref in refs_b]

    changes: list[dict[str, Any]] = []
    format_changes: list[dict[str, Any]] = []
    matcher = difflib.SequenceMatcher(None, texts_a, texts_b, autojunk=False)
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            for offset in range(i2 - i1):
                ref_a = refs_a[i1 + offset]
                ref_b = refs_b[j1 + offset]
                if level == "format" and _para_signature(ref_a.element) != _para_signature(
                    ref_b.element
                ):
                    format_changes.append(
                        {
                            "type": "formatChanged",
                            "a": ref_a.path,
                            "b": ref_b.path,
                            "text": ref_a.text[:80],
                        }
                    )
            continue

        if tag == "replace":
            if (i2 - i1) == (j2 - j1):
                for offset in range(i2 - i1):
                    changes.append(
                        {
                            "type": "replaced",
                            "a": refs_a[i1 + offset].path,
                            "b": refs_b[j1 + offset].path,
                            "old": texts_a[i1 + offset],
                            "new": texts_b[j1 + offset],
                        }
                    )
            else:
                for index in range(i1, i2):
                    changes.append(
                        {"type": "deleted", "a": refs_a[index].path, "old": texts_a[index]}
                    )
                for index in range(j1, j2):
                    changes.append(
                        {"type": "inserted", "b": refs_b[index].path, "new": texts_b[index]}
                    )
        elif tag == "delete":
            for index in range(i1, i2):
                changes.append({"type": "deleted", "a": refs_a[index].path, "old": texts_a[index]})
        elif tag == "insert":
            for index in range(j1, j2):
                changes.append({"type": "inserted", "b": refs_b[index].path, "new": texts_b[index]})

    summary = {
        "inserted": sum(1 for item in changes if item["type"] == "inserted"),
        "deleted": sum(1 for item in changes if item["type"] == "deleted"),
        "replaced": sum(1 for item in changes if item["type"] == "replaced"),
    }

    report: dict[str, Any] = {
        "level": level,
        "identical": not changes,
        "summary": summary,
        "changes": changes,
        "structure": {
            "a": model_a.structure(),
            "b": model_b.structure(),
        },
    }

    if level == "format":
        sections_a = [_section_info(section) for section in document_a.sections]
        sections_b = [_section_info(section) for section in document_b.sections]
        report["format"] = {
            "formatChanged": len(format_changes),
            "changes": format_changes,
            "pageSetupEqual": sections_a == sections_b,
            "sections": {"a": sections_a, "b": sections_b},
            "paragraphCountEqual": len(refs_a) == len(refs_b),
        }
        report["identical"] = not changes and not format_changes

    return report


# --------------------------------------------------------------------------- #
# 写入：单元格 / 段落
# --------------------------------------------------------------------------- #


def set_cell_text(
    model: DocModel,
    cell: dict[str, Any],
    text: str,
    *,
    append: bool = False,
) -> dict[str, Any]:
    """写入单元格文本，保留单元格内首个段落的格式。"""
    tc = cell["tc"]
    paragraphs = tc.findall(_w("p"))
    lines = str(text).split("\n")

    if not paragraphs:
        paragraph = _new_paragraph(lines[0])
        tc.append(paragraph)
        anchor = paragraph
    elif append:
        target = paragraphs[-1]
        _append_text_to_paragraph(target, str(text))
        return {"location": cell["path"], "mode": "append", "text": str(text)}
    else:
        first = paragraphs[0]
        _clear_paragraph(first, lines[0])
        for extra in paragraphs[1:]:
            parent = extra.getparent()
            if parent is not None:
                parent.remove(extra)
        anchor = first

    for line in lines[1:]:
        new_paragraph = _new_paragraph(line, template=anchor if paragraphs else None)
        anchor.addnext(new_paragraph)
        anchor = new_paragraph

    return {"location": cell["path"], "mode": "replace", "lines": len(lines), "text": str(text)}


def insert_paragraph(
    model: DocModel,
    *,
    text: str,
    after: Optional[str] = None,
    before: Optional[str] = None,
    style_from: Optional[str] = None,
) -> dict[str, Any]:
    """在指定位置插入段落（默认插到文档末尾），可继承参考段落的格式。"""
    body = model.body
    template_ref: Optional[ParagraphRef] = None
    if style_from:
        template_ref = model.paragraph(str(style_from))

    anchor: Any = None
    position = "append"
    if after:
        ref = model.paragraph(str(after))
        if ref is None:
            raise ValueError(f"找不到段落：{after}")
        anchor = ref.element
        position = "after"
        if template_ref is None:
            template_ref = ref
    elif before:
        ref = model.paragraph(str(before))
        if ref is None:
            raise ValueError(f"找不到段落：{before}")
        anchor = ref.element
        position = "before"
        if template_ref is None:
            template_ref = ref

    lines = str(text).split("\n")
    created: list[Any] = []
    cursor = anchor
    for line in lines:
        paragraph = _new_paragraph(line, template=template_ref.element if template_ref else None)
        created.append(paragraph)
        if cursor is None:
            body.append(paragraph)
        else:
            cursor.addnext(paragraph)
            cursor = paragraph

    if position == "before" and anchor is not None and created:
        # before 场景下 cursor 从 anchor 开始 addnext 会落到 anchor 之后，
        # 这里把新建段落按原顺序整体移到 anchor 之前
        for paragraph in created:
            parent = paragraph.getparent()
            if parent is not None:
                parent.remove(paragraph)
        for paragraph in created:
            anchor.addprevious(paragraph)

    return {
        "inserted": len(created),
        "position": position,
        "after": after,
        "before": before,
        "styleFrom": style_from,
    }


def delete_paragraph(model: DocModel, location: str) -> dict[str, Any]:
    """删除一个段落。"""
    ref = model.paragraph(location)
    if ref is None:
        raise ValueError(f"找不到段落：{location}")
    parent = ref.element.getparent()
    if parent is None:
        raise ValueError(f"段落 {location} 没有父节点，无法删除")
    text = ref.text
    parent.remove(ref.element)
    return {"deleted": ref.path, "text": text}


# --------------------------------------------------------------------------- #
# 字段填充
# --------------------------------------------------------------------------- #


def _replace_first_across(model: DocModel, old: str, new: str) -> int:
    """在全文做单次字面替换，返回命中次数（用于占位符）。"""
    report = replace_rules(model, [{"old": old, "new": new}], order="as-given")
    return sum(entry["matched"] for entry in report["results"])


def _fill_label(
    model: DocModel, label: str, value: str, overwrite: bool
) -> tuple[bool, Optional[dict[str, Any]]]:
    """「标签：____」式空字段填充：把标签后冒号起的内容替换为 value。"""
    for ref in model.paragraphs_all:
        text = ref.text
        index = text.find(label)
        if index < 0:
            continue
        rest = text[index + len(label) :]
        match = re.match(r"\s*[：:]\s*", rest)
        if match is None:
            continue
        start = index + len(label) + match.end()
        current = text[start:].strip()
        if current and not overwrite:
            return False, {
                "location": ref.path,
                "reason": f"已有内容：{current[:20]}",
                "text": text[:60],
            }
        _splice_paragraph(ref.element, start, len(text), str(value))
        return True, {"location": ref.path, "text": f"{label}：{value}"[:60]}
    return False, None


def fill_fields(
    model: DocModel,
    fields: dict[str, str],
    *,
    overwrite: bool = False,
) -> dict[str, Any]:
    """填充封面/表单字段。

    两种模式，按顺序尝试：
    1. **占位符**：``{{课程名称}}`` / ``${课程名称}`` / ``【课程名称】``；
    2. **标签**：找到「课程名称：」后冒号起的内容，为空或 ``overwrite=True``
       时写入新值。

    比「先转 HTML 再全局字符串替换」稳得多：只在冒号之后落字，不会碰到
    标题、正文里同名的词。
    """
    filled: list[dict[str, Any]] = []
    skipped: list[dict[str, Any]] = []
    missed: list[str] = []

    for key, value in (fields or {}).items():
        value = "" if value is None else str(value)
        done = False

        for placeholder in (
            f"{{{{{key}}}}}",
            f"{{{{ {key} }}}}",
            f"${{{key}}}",
            f"【{key}】",
        ):
            count = _replace_first_across(model, placeholder, value)
            if count:
                filled.append(
                    {"field": key, "mode": "placeholder", "placeholder": placeholder, "count": count}
                )
                done = True
                break

        if done:
            continue

        ok, detail = _fill_label(model, key, value, overwrite)
        if ok:
            filled.append({"field": key, "mode": "label", **(detail or {})})
        elif detail:
            skipped.append({"field": key, **detail})
        else:
            missed.append(key)

    return {"filled": filled, "skipped": skipped, "missed": missed}


# --------------------------------------------------------------------------- #
# 校验
# --------------------------------------------------------------------------- #


def validate_document(
    model: DocModel,
    *,
    required_keywords: Optional[list[str]] = None,
    forbidden_keywords: Optional[list[str]] = None,
) -> dict[str, Any]:
    """体检报告：占位符残留、空单元格、关键词计数、页面设置完整性。"""
    placeholders: list[dict[str, Any]] = []
    for ref in model.paragraphs_all:
        text = ref.text
        if not text:
            continue
        for pattern, kind in _PLACEHOLDER_PATTERNS:
            for match in re.finditer(pattern, text):
                placeholders.append(
                    {
                        **ref.location(),
                        "kind": kind,
                        "text": match.group()[:40],
                        "paragraphText": text[:80],
                    }
                )

    empty_cells: list[dict[str, Any]] = []
    for table in model.tables:
        for item in table.cells:
            content = "".join(ref.text for ref in item.get("paragraphs", []))
            if not content.strip():
                empty_cells.append(
                    {
                        "location": item["path"],
                        "tableIndex": table.index,
                        "row": item["row"],
                        "col": item["col"],
                    }
                )

    all_text = "\n".join(ref.text for ref in model.paragraphs_all)
    keyword_counts = {keyword: all_text.count(keyword) for keyword in (required_keywords or [])}
    forbidden = {
        keyword: count
        for keyword in (forbidden_keywords or [])
        if (count := all_text.count(keyword))
    }

    sections = [_section_info(section) for section in model.document.sections]
    page_setup_ok = all(
        section["pageWidthPt"] and section["pageHeightPt"] for section in sections
    )

    missing_keywords = [key for key, value in keyword_counts.items() if value == 0]
    return {
        "ok": not placeholders and not forbidden and not missing_keywords and page_setup_ok,
        "structure": model.structure(),
        "placeholderCount": len(placeholders),
        "placeholders": placeholders,
        "emptyCellCount": len(empty_cells),
        "emptyCells": empty_cells,
        "keywordCounts": keyword_counts,
        "missingKeywords": missing_keywords,
        "forbiddenKeywordCounts": forbidden,
        "pageSetupOk": page_setup_ok,
        "sections": sections,
        "styleCount": len(model.document.styles),
    }
