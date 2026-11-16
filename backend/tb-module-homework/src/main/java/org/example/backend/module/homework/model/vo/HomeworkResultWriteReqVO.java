package org.example.backend.module.homework.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 批改结果回写请求 VO（执行终态后由前端调用）
 *
 * <p>覆盖式：同一任务重复回写以最后一次为准。终态状态机（2 成功 / 3 部分成功 / 4 失败）
 * 与漏批份数由**服务端**从 records 数量对比任务 fileCount 推导，请求体不携带，避免双头真相。
 */
@Data
public class HomeworkResultWriteReqVO {

    /** 产出本批结果的工作流会话 id（溯源，可空） */
    private String conversationId;
    /** 汇总节点的产出（班级统计等，仅记录日志不落库，可空） */
    private Map<String, Object> statistics;
    /** 逐学生评分记录（执行失败收敛终态时为空列表） */
    private List<HomeworkResultRecordVO> records;

}
