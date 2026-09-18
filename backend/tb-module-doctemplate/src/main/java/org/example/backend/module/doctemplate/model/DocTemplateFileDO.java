package org.example.backend.module.doctemplate.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档模板生成结果文件 DO（表 doc_template_file）
 */
@Data
public class DocTemplateFileDO {

    /** 主键（自增） */
    private Long id;
    /** 所属生成任务 id（doc_template_generation.id） */
    private Long generationId;
    /** 结果文件 id（infra_file.id） */
    private Long fileId;
    /** 结果文件名（含扩展名） */
    private String name;
    /** 文档类型：WORD / PDF / MARKDOWN */
    private String type;
    /** 文件大小（字节） */
    private Long size;
    /** 份数定位标签：复刻 / 优化 / 精简… */
    private String tag;
    /** 内容摘要（结果卡片副标题） */
    private String summary;
    /** 展示顺序（0 起） */
    private Integer sort;
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
