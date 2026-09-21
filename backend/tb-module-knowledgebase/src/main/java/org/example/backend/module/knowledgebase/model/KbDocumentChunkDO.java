package org.example.backend.module.knowledgebase.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档分片 DO（表 kb_document_chunk）
 *
 * <p>分片由 AI 语义分片产生，不携带类型/标题信息：版面类型属于解析块（kb_document_parse_block.block_type）。
 */
@Data
public class KbDocumentChunkDO {

    /** 主键（自增） */
    private Long id;
    /** 所属文档 id（kb_document.id） */
    private Long documentId;
    /** 冗余空间 id，便于按空间过滤 */
    private Long spaceId;
    /** 分片序号（从 0 开始） */
    private Integer chunkIndex;
    /** 分片正文 */
    private String content;
    /** token 数 */
    private Integer tokenCount;
    /** 是否参与检索（单条分片启停，替代删除） */
    private Boolean enabled;
    /** 召回次数 */
    private Integer hitCount;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 id */
    private Long creator;
    /** 更新人 id */
    private Long updater;

}
