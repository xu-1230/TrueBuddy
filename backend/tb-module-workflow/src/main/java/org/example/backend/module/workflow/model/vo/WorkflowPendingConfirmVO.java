package org.example.backend.module.workflow.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 待确认清单：执行到人工确认节点暂停时对外暴露的交互数据。
 *
 * <p>出现位置：
 * <ul>
 *   <li>SSE 的 {@code message} 事件（{@code type = requires_action}）；</li>
 *   <li>{@code GET /workflow/get_process} 的 {@code pendingConfirm}（状态为 4 时）。</li>
 * </ul>
 *
 * <p>调用方按 {@link #plan} 渲染清单、按 {@link #defaultPlan} 默认勾选，提交时把勾选出的
 * {@code filePath} 列表作为 {@code confirmedPlan} 回传。
 */
@Data
public class WorkflowPendingConfirmVO {

    /** 暂停节点的 id（人工确认节点） */
    private String nodeId;

    /** 暂停节点标题 */
    private String nodeName;

    /** 规划出的批改清单（全部条目，含无需批改项） */
    private List<PlanItemVO> plan = new ArrayList<>();

    /** 默认勾选清单（仅 {@code needGrading = true} 的文件路径） */
    private List<String> defaultPlan = new ArrayList<>();

}
