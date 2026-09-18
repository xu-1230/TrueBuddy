package org.example.backend.module.infra.model.vo;

import lombok.Data;

/**
 * 文件上传返回 VO
 */
@Data
public class FileUploadRespVO {

    /** 文件 id（infra_file 主键，供后续业务引用） */
    private Long id;
    /** 原始文件名（含扩展名） */
    private String name;
    /** 文件大小（字节） */
    private Long size;
    /** 文件类型（MIME） */
    private String type;
    /** 访问地址（MinIO 预签名 URL，7 天有效） */
    private String url;

}
