package org.example.backend.module.knowledgebase.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档 Response VO（对应前端 DocumentVO）
 */
@Data
public class DocumentRespVO {

    /** 文档 id */
    private Long id;
    /** 所属空间 id */
    private Long spaceId;
    /** 文档名称 */
    private String name;
    /** 文档类型：PDF / WORD / MARKDOWN */
    private String type;
    /** 创建时间 */
    private LocalDateTime createTime;

}
