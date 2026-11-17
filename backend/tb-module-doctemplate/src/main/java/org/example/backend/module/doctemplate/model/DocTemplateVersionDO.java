package org.example.backend.module.doctemplate.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档编辑版本 DO（表 doc_template_version）
 * <p>
 * 根文件即 v1，本表只存 v2+ 的不可变版本；parentVersionId 为空表示父级是 v1 根文件。
 */
@Data
public class DocTemplateVersionDO {

    /** 主键（自增） */
    private Long id;
    /** 根文件 id（doc_template_file.id，即工作区） */
    private Long rootFileId;
    /** 该版本 docx（infra_file.id） */
    private Long fileId;
    /** 父版本 id；null 表示父级是 v1 根文件 */
    private Long parentVersionId;
    /** 版本号：2、3…；v1 由根文件承担 */
    private Integer revision;
    /** 本次修改摘要（审计 / 撤回提示） */
    private String editSummary;
    /** 结构化编辑指令 JSON（审计用） */
    private String editOps;
    /** 编辑人 id */
    private Long creator;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 是否已被撤回 / 级联失效 */
    private Boolean superseded;

}
