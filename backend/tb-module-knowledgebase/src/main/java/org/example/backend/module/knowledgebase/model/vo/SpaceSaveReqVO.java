package org.example.backend.module.knowledgebase.model.vo;

import lombok.Data;

/**
 * 知识库空间 新增/修改 Request VO
 */
@Data
public class SpaceSaveReqVO {

    /** 空间 id（修改时必填） */
    private Long id;
    /** 空间名称 */
    private String name;
    /** 空间描述 */
    private String description;

}
