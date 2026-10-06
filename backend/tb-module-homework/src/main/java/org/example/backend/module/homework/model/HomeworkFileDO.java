package org.example.backend.module.homework.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业文件 DO（表 homework_file）
 * <p>任务下的每一份作业；file_id 关联 infra_file.id（逻辑外键，不建约束）。
 * relative_path 为本模块的产出核心，如「电子技术基础-第3次作业/20230101-张三/第三章习题.docx」。
 */
@Data
public class HomeworkFileDO {

    /** 主键（自增） */
    private Long id;
    /** 所属任务 id（homework_task.id） */
    private Long taskId;
    /** 关联文件 id（infra_file.id，可空） */
    private Long fileId;
    /** 相对路径（含根目录，/ 分隔） */
    private String relativePath;
    /** 文件基础名（含扩展名） */
    private String name;
    /** 文件大小（字节） */
    private Long size;
    /** 创建时间 */
    private LocalDateTime createTime;

}
