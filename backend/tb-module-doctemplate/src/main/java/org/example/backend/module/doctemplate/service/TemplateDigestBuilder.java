package org.example.backend.module.doctemplate.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模板段落清单构建器。
 * <p>
 * docx-mcp 的 {@code docx_get_tables} 返回里 {@code cells[].paragraphs[]} 与 {@code grid} 内容重复，
 * 直接回传给 LLM 既冗长又容易被截断（截断后子表内容整段丢失）。这里把它压成
 * 「段落路径 → 段落原文」的扁平行清单：既是 old 的唯一正确来源，也是供模型选用的 scope 取值来源，
 * 并且天然覆盖嵌套子表（子表在 {@code tables[]} 中各有独立 tableIndex）。
 */
@Slf4j
@Component
public class TemplateDigestBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * docx_get_tables 的返回 → 「段落路径 \t 原文」清单（含全部嵌套子表，丢弃 grid 与空段落）。
     */
    public String digestTables(String tablesJson) {
        if (tablesJson == null || tablesJson.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try {
            JsonNode root = objectMapper.readTree(tablesJson);
            JsonNode tables = root.get("tables");
            if (tables == null || !tables.isArray()) {
                return "";
            }
            for (JsonNode table : tables) {
                JsonNode cells = table.get("cells");
                if (cells == null || !cells.isArray() || cells.isEmpty()) {
                    continue;
                }
                builder.append("### t:").append(table.path("tableIndex").asInt())
                        .append(" (").append(table.path("rowCount").asInt())
                        .append("行 x ").append(table.path("colCount").asInt()).append("列)\n");
                for (JsonNode cell : cells) {
                    JsonNode paragraphs = cell.get("paragraphs");
                    if (paragraphs == null || !paragraphs.isArray()) {
                        continue;
                    }
                    for (JsonNode paragraph : paragraphs) {
                        String text = paragraph.path("text").asText("");
                        if (text.isBlank()) {
                            continue;
                        }
                        builder.append(paragraph.path("location").asText(""))
                                .append('\t')
                                .append(oneLine(text))
                                .append('\n');
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[digestTables] 表格清单构建失败，回退为空清单: {}", e.getMessage());
            return "";
        }
        return builder.toString();
    }

    /**
     * docx_get_text 单页返回 → 「位置 \t 原文」清单（只取正文段落；表格内容由 {@link #digestTables} 提供）。
     */
    public String digestTextBlocks(String textJson) {
        if (textJson == null || textJson.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try {
            JsonNode root = objectMapper.readTree(textJson);
            JsonNode blocks = root.get("blocks");
            if (blocks == null || !blocks.isArray()) {
                return "";
            }
            for (JsonNode block : blocks) {
                if (!"paragraph".equals(block.path("kind").asText(""))) {
                    continue;
                }
                String text = block.path("text").asText("");
                if (text.isBlank()) {
                    continue;
                }
                builder.append(block.path("location").asText(""))
                        .append('\t')
                        .append(oneLine(text))
                        .append('\n');
            }
        } catch (Exception e) {
            log.warn("[digestTextBlocks] 正文清单构建失败，回退为空清单: {}", e.getMessage());
            return "";
        }
        return builder.toString();
    }

    /** docx_get_text 返回中的总页数（解析失败按 1 页处理） */
    public int pages(String textJson) {
        if (textJson == null || textJson.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, objectMapper.readTree(textJson).path("pages").asInt(1));
        } catch (Exception e) {
            return 1;
        }
    }

    /** 段落原文压成单行，避免清单被换行拆散 */
    private String oneLine(String text) {
        return text.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

}
