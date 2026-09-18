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
    /** 处理状态：0=待处理，1=解析中，2=已完成，3=失败 */
    private Integer status;
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
