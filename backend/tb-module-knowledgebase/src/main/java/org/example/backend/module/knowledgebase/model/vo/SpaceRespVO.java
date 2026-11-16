package org.example.backend.module.knowledgebase.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库空间 Response VO（对应前端 SpaceVO）
 */
@Data
public class SpaceRespVO {

    /** 空间 id */
    private Long id;
    /** 空间名称 */
    private String name;
    /** 空间描述 */
    private String description;
    /** 可见性：0=私有，1=公有 */
    private Integer visibility;
    /** 文档数量 */
    private Long documentCount;
    /** 创建时间 */
    private LocalDateTime createTime;

}
