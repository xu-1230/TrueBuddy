package org.example.backend.module.knowledgebase.model.vo;

import lombok.Data;

/**
 * 知识库文档分片 Response VO（对应前端 ChunkVO）
 */
@Data
public class ChunkRespVO {

    /** 分片 id */
    private Long id;
    /** 所属文档 id */
    private Long documentId;
    /** 分片序号（从 0 开始），对应前端 ChunkVO.index */
    private Integer index;
    /** 分片标题 */
    private String title;
    /** 分片正文 */
    private String content;

}
