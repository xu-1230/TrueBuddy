package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生成结果文件 VO（对应前端结果卡片）
 */
@Data
public class DocFileRespVO {

    /** 结果记录 id（doc_template_file 主键） */
    private Long id;
    /** 文件 id（infra_file 主键） */
    private Long fileId;
    /** 结果文件名（含扩展名） */
    private String name;
    /** 文档类型：WORD / PDF / MARKDOWN */
    private String type;
    /** 文件大小（字节） */
    private Long size;
    /** 份数定位标签：复刻 / 优化 / 精简… */
    private String tag;
    /** 内容摘要 */
    private String summary;
    /** 访问地址（MinIO 预签名 URL） */
    private String src;
    /** 创建时间 */
    private LocalDateTime createTime;

}
