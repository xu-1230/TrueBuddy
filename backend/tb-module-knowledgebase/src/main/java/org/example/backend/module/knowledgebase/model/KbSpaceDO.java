package org.example.backend.module.knowledgebase.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库空间 DO（表 kb_space）
 */
@Data
public class KbSpaceDO {

    /** 主键（自增） */
    private Long id;
    /** 空间名称 */
    private String name;
    /** 空间描述 */
    private String description;
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
