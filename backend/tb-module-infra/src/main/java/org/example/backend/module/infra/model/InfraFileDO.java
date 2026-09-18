package org.example.backend.module.infra.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件 DO（表 infra_file，MinIO 对象元数据）
 */
@Data
public class InfraFileDO {

    /** 主键（自增） */
    private Long id;
    /** 存储桶（MinIO bucket） */
    private String bucket;
    /** 对象键（MinIO objectKey） */
    private String path;
    /** 原始文件名（含扩展名） */
    private String name;
    /** 访问地址（预签名 URL） */
    private String url;
    /** 文件大小（字节） */
    private Long size;
    /** 文件类型（MIME） */
    private String type;
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
