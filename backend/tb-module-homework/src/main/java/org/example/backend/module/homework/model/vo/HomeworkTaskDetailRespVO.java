package org.example.backend.module.homework.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 批改任务详情 Response VO（历史抽屉点条目后的只读回看：配置 + 文件树）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HomeworkTaskDetailRespVO extends HomeworkTaskItemVO {

    /** 任务下全部作业（按相对路径升序，即目录树阅读顺序；含预签名 URL 与 MIME） */
    private List<HomeworkFileItemVO> files;
    /** 逐学生评分记录（按名单顺序；未回写终态时为空列表） */
    private List<HomeworkResultRecordVO> results;
    /** 漏批份数（fileCount - 评分记录数，负值归 0） */
    private Integer missedCount;

}
