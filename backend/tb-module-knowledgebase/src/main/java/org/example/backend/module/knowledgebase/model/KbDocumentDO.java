package org.example.backend.module.knowledgebase.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档 DO（表 kb_document）
 */
@Data
public class KbDocumentDO {

    /** 主键（自增） */
    private Long id;
    /** 所属空间 id（kb_space.id） */
    private Long spaceId;
    /** 关联文件 id（infra_file.id，可空） */
    private Long fileId;
    /** 文档名称 */
    private String name;
    /** 文档类型：PDF / WORD / MARKDOWN */
    private String type;
    /** 分片数量 */
    private Integer chunkCount;
    /** 文档 token 总量 */
    private Integer tokenCount;
    /** 处理状态：0=待处理，1=解析中，2=已完成（已向量化），3=失败，4=分片待确认，5=解析完成待分片，6=AI分片中，7=向量化中 */
    private Integer status;
    /** 解析任务句柄：云端为文档智能业务订单号，本地为内存任务 id */
    private String docmindJobId;
    /** 解析进度 0-100 */
    private Integer parseProgress;
    /** 解析失败原因 */
    private String parseError;
    /** 解析开始时间 */
    private LocalDateTime parseStartAt;
    /** 解析结束时间 */
    private LocalDateTime parseEndAt;
    /** 向量化完成时间 */
    private LocalDateTime vectorizeAt;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 id */
    private Long creator;
    /** 更新人 id */
    private Long updater;
    /** 删除时间，null 表示未删除 */
    private LocalDateTime deletedAt;

}
