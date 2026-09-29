package org.example.backend.module.workflow.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流业务执行结果：执行结束后由 {@code /workflow/get_process} 的 {@code result} 字段携带。
 *
 * <p>执行状态与耗时属于「进度」语义，不在本 VO 内重复定义，见
 * {@link WorkflowProcessRespVO#getExeHistoryStatus()} / {@link WorkflowProcessRespVO#getExeCostMs()}。
 */
@Data
public class WorkflowRunRespVO {

    /** 规划节点产出的批改清单（全部条目，含无需批改项） */
    private List<PlanItemVO> plan = new ArrayList<>();

    /** 默认批改清单（仅 needGrading = true 的文件路径） */
    private List<String> defaultPlan = new ArrayList<>();

    /** 本次实际批改的清单（人工确认提交后写入状态的值） */
    private List<String> confirmedPlan;

    /** 逐份批改结果 */
    private List<GradingRecordVO> records = new ArrayList<>();

    /** 结束节点渲染出的最终展示内容 */
    private String content;

    /** 本次批改满分 */
    private Integer fullScore;

    /** 已执行节点顺序（联调用） */
    private List<String> nodeTrace = new ArrayList<>();

    /** 最终状态快照（键为 {nodeId}.{outputName}，联调用） */
    private Map<String, Object> state = new LinkedHashMap<>();

}
