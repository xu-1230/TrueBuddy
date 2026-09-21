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
    /** 处理状态：0=待处理，1=解析中，2=已完成（已向量化），3=失败，4=待确认（已解析未向量化） */
    private Integer status;
    /** 解析进度 0-100 */
    private Integer parseProgress;
    /** 解析失败原因 */
    private String parseError;
    /** 创建时间 */
    private LocalDateTime createTime;

}
