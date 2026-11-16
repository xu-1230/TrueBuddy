package org.example.backend.module.homework.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批改任务列表项 Response VO（历史抽屉列表条目）
 */
@Data
public class HomeworkTaskItemVO {

    /** 任务 id（homework_task.id） */
    private Long id;
    /** 作业文件夹名 */
    private String folderName;
    /** 任务状态：0=已上传待批改，1=批改中，2=成功，3=部分成功，4=失败 */
    private Integer status;
    /** 作业份数 */
    private Integer fileCount;
    /** 文件总大小（字节） */
    private Long totalSize;
    /** AI 描述说明（批改要求，批改请求写入前为 null） */
    private String description;
    /** 知识库 id 列表（批改请求写入前为空列表） */
    private List<String> spaceIds;
    /** 评分记录数（已回写终态时 > 0；未回写时为 0） */
    private Integer resultCount;
    /** 创建时间 */
    private LocalDateTime createTime;

}
