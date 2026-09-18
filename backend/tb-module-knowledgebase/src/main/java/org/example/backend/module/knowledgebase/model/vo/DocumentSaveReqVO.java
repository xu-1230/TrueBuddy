package org.example.backend.module.knowledgebase.model.vo;

import lombok.Data;

/**
 * 知识库文档 新增 Request VO
 */
@Data
public class DocumentSaveReqVO {

    /** 所属空间 id */
    private Long spaceId;
    /** 文档名称 */
    private String name;
    /** 文档类型：PDF / WORD / MARKDOWN */
    private String type;

}
