package org.example.backend.module.homework.model.vo;

import lombok.Data;

/**
 * 作业文件 Response VO（任务下的一份作业）
 */
@Data
public class HomeworkFileItemVO {

    /** 作业文件 id（homework_file.id） */
    private Long id;
    /** 关联文件 id（infra_file.id，可空） */
    private Long fileId;
    /** 文件基础名（含扩展名） */
    private String name;
    /** 相对路径，如 电子技术基础-第3次作业/20230101-张三/第三章习题.docx */
    private String path;
    /** 文件大小（字节） */
    private Long size;
    /** 文件类型（MIME） */
    private String type;
    /** 访问地址（MinIO 预签名 URL，7 天有效） */
    private String url;

}
