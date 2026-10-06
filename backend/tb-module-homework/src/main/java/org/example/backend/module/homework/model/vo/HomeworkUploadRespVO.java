package org.example.backend.module.homework.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 作业文件夹上传 Response VO
 * <p>paths 为以相对路径表示的文件清单（前端结果区直接展示）；
 * files 附带每份文件的 fileId / 访问地址，供后续批改与「打开原文件」使用。
 */
@Data
public class HomeworkUploadRespVO {

    /** 批改任务 id（homework_task.id） */
    private Long taskId;
    /** 作业文件夹名 */
    private String folderName;
    /** 作业份数 */
    private Integer fileCount;
    /** 文件总大小（字节） */
    private Long totalSize;
    /** 文件相对路径清单，如 电子技术基础-第3次作业/20230101-张三/第三章习题.docx */
    private List<String> paths;
    /** 每份文件的明细 */
    private List<HomeworkFileItemVO> files;

}
