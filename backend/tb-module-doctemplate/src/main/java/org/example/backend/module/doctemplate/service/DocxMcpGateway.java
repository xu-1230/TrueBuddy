package org.example.backend.module.doctemplate.service;

import org.example.backend.module.doctemplate.model.vo.DocxReplaceReport;
import org.example.backend.module.doctemplate.model.vo.DocxValidateResult;

import java.util.List;
import java.util.Map;

/**
 * docx-mcp 程序化调用网关。
 * <p>
 * 隔离 MCP 调用细节（工具名 / 参数 / 返回解包），使上层业务只关注
 * 「解析模板结构 → LLM 产出按位置区分的规则 → dryRun 预演 → 落盘 → 自检」流程。
 */
public interface DocxMcpGateway {

    /**
     * docx_open：读取模板骨架（结构 / 页面设置 / 标题 / 封面字段 / 表格概览）。
     *
     * @param source 模板来源（本地绝对路径或 http(s) 直链）
     * @return JSON 文本
     */
    String openOutline(String source);

    /**
     * docx_get_text：读取模板纯文本（带位置索引），供 LLM 精确挑选需要改写的原文。
     *
     * @param source   模板来源
     * @param page     页码（1 起，可为空表示不分页）
     * @param pageSize 每页块数
     * @return JSON 文本
     */
    String getText(String source, Integer page, Integer pageSize);

    /**
     * docx_get_tables：读取表格结构（含嵌套表格），给出单元格与其内部段落的定位信息，
     * 供 LLM 按位置（table/row/col 或段落路径）指定作用域。
     *
     * @param source 模板来源
     * @return JSON 文本
     */
    String getTables(String source);

    /**
     * docx_replace：带作用域的批量文本替换，直接产出新 docx。
     * <p>
     * 该工具只修改 {@code w:t} 文本，不动 {@code w:pPr} / {@code w:rPr}，
     * 字体、字号、缩进、编号与页面设置与模板完全一致，是「只改文字、版式零变化」的保真路径。
     *
     * @param source     模板来源
     * @param rules      替换规则列表，每条形如 {@code {"old":"...","new":"...","scope":{...}}}
     * @param outputPath 目标 docx 绝对路径（dryRun=true 时忽略）
     * @param dryRun     true 只预演返回命中报告、不写盘；false 落盘
     * @return 结构化变更报告（dryRun=false 时其 {@code outputPath} 为落盘路径）
     */
    DocxReplaceReport replace(String source, List<Map<String, Object>> rules, String outputPath, boolean dryRun);

    /**
     * docx_validate：文档体检（结构 / 占位符残留 / 空单元格 / 关键词计数 / 页面设置）。
     *
     * @param source           文档来源
     * @param requiredKeywords 必须具备的关键词（新主题词）
     * @param forbiddenKeywords 必须消失的关键词（旧主题词）
     * @return 体检结果
     */
    DocxValidateResult validate(String source, List<String> requiredKeywords, List<String> forbiddenKeywords);

    /**
     * docx_fill_fields：填充封面 / 表单字段（{@code {{占位符}}} 或「标签：」后空值）。
     * <p>
     * 仅用于把「用户明确给出的主题字段」（如课程名称 / 章节名称 / 面向专业）写入空封面；
     * 人事与日期类字段（任课教师、日期、签字等）不调用本方法，以免编造。
     *
     * @param source     文档来源
     * @param fields     字段字典，如 {@code {"课程名称":"软件工程"}}
     * @param outputPath 目标路径（传 null 表示就地写回 source）
     * @param overwrite  true 覆盖已有内容；false 只填空字段
     * @return 落盘后的 docx 绝对路径
     */
    String fillFields(String source, Map<String, String> fields, String outputPath, boolean overwrite);

}
