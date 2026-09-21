package org.example.backend.module.knowledgebase.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档解析版面块 DO（表 kb_document_parse_block）
 *
 * <p>docmind 解析结果的持久化产物（块级）：既是 AI 语义分片的输入，也是页码等溯源信息的载体。
 */
@Data
public class KbDocumentParseBlockDO {

    /** 主键（自增） */
    private Long id;
    /** 所属文档 id（kb_document.id） */
    private Long documentId;
    /** 冗余空间 id，便于按空间过滤 */
    private Long spaceId;
    /** 全局阅读顺序（从 0 开始），即 AI 分片的输入顺序 */
    private Integer blockIndex;
    /** 页码（LayoutVO.pageNum） */
    private Integer pageNum;
    /** 页内序号（LayoutVO.index） */
    private Integer pageBlockSeq;
    /** 版面类型，取值开放（title / text / table / figure ...） */
    private String blockType;
    /** 块的 Markdown 正文 */
    private String content;
    /** 块的纯文本（Markdown 缺失时作降级） */
    private String text;
    /** 解析引擎返回的块唯一 ID（LayoutVO.uniqueId），用于溯源 */
    private String externalId;
    /** 估算 token 数 */
    private Integer tokenCount;
    /** 创建时间 */
    private LocalDateTime createTime;

}
