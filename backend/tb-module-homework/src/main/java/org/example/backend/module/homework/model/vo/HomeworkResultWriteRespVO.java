package org.example.backend.module.homework.model.vo;

import lombok.Data;

/**
 * 批改结果回写响应 VO：终态与漏批统计（由服务端推导）
 */
@Data
public class HomeworkResultWriteRespVO {

    /** 任务 id */
    private Long taskId;
    /** 回写后的任务状态：2=成功，3=部分成功（有漏批），4=失败 */
    private Integer status;
    /** 落库的评分记录数 */
    private Integer total;
    /** 漏批份数（fileCount - records 数量，负值归 0） */
    private Integer missed;

}
