package org.example.backend.module.doctemplate.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档模板生成任务 DO（表 doc_template_generation）
 */
@Data
public class DocTemplateGenerationDO {

    /** 主键（自增） */
    private Long id;
    /** 模板文件 id（infra_file.id） */
    private Long templateFileId;
    /** 知识库空间 id 列表（逗号分隔；为空表示不启用检索） */
    private String spaceIds;
    /** AI 描述说明 */
    private String description;
    /** 清晰度参数：480p / 720p / 1080p */
    private String quality;
    /** 实际产出文档数量 */
    private Integer docCount;
    /** 任务状态：0=生成中，1=成功，2=部分成功，3=失败 */
    private Integer status;
    /** 失败/部分失败的错误摘要 */
    private String errorMsg;
    /** 任务耗时（毫秒） */
    private Long durationMs;
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
