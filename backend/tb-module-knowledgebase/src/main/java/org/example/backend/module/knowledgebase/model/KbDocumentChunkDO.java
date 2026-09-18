package org.example.backend.module.knowledgebase.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档分片 DO（表 kb_document_chunk）
 */
@Data
public class KbDocumentChunkDO {

    /** 主键（自增） */
    private Long id;
    /** 所属文档 id（kb_document.id） */
    private Long documentId;
    /** 分片序号（从 0 开始） */
    private Integer chunkIndex;
    /** 分片标题 */
    private String title;
    /** 分片正文 */
    private String content;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 id */
    private Long creator;
    /** 更新人 id */
    private Long updater;
    /** 是否删除：false=否，true=是 */
    private Boolean deleted;

}
