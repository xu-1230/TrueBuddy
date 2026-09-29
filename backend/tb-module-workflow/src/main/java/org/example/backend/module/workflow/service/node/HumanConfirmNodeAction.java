package org.example.backend.module.workflow.service.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.workflow.model.schema.InputParamDef;
import org.example.backend.module.workflow.model.schema.InputSlotDef;
import org.example.backend.module.workflow.model.schema.OutputParamDef;
import org.example.backend.module.workflow.model.schema.SchemaNodeDef;
import org.example.backend.module.workflow.model.vo.PlanItemVO;
import org.example.backend.module.workflow.service.graph.LlmPayloadParser;
import org.example.backend.module.workflow.service.graph.RefResolver;
import org.example.backend.module.workflow.service.graph.WorkflowStateView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 人工确认节点：执行到此**暂停**，等待调用方提交确认清单后继续。
 *
 * <p>实现 {@link InterruptableAction}：节点执行前框架回调 {@link #interrupt}，若清单尚未确认则返回
 * 中断元数据，图执行停在本次节点（SSE 推 {@code requires_action}，会话状态置「等待用户输入」）。
 * 调用方提交 {@code confirmedPlan} 后由恢复链路
 * {@code updateState(config, {{nodeId}.{outputName}: confirmedPlan}, nodeId)} + {@code stream(null, resumeConfig)}
 * 从本节点继续；此时 {@link #apply} 检测到状态中已有清单即**原样写回**，下游循环与
 * {@code confirmedPlan} 回显口径完全不变。
 *
 * <p>以下情形不中断、直接放行：
 * <ol>
 *   <li>状态中已有确认清单（恢复执行 / 外部注入，见 {@link #apply} 首分支）；</li>
 *   <li>配置里已带人工反馈（恢复调用的兜底保险，避免同一节点被反复中断）；</li>
 *   <li>未声明 {@code plan} 输入，或规划结果为空 —— 没有可确认的内容，无需打断用户。</li>
 * </ol>
 */
@Slf4j
public class HumanConfirmNodeAction implements AsyncNodeActionWithConfig, InterruptableAction {

    /** 约定：确认清单的输入变量名 */
    private static final String PLAN_INPUT_NAME = "plan";

    /** 约定：确认清单的输出变量名 */
    private static final String DEFAULT_OUTPUT_NAME = "confirmedPlan";

    /** 中断元数据键：规划出的批改清单（{@code List<PlanItemVO>}），供上层组装待确认清单 */
    public static final String METADATA_PLAN = "plan";

    /** 中断元数据键：默认勾选的文件路径（{@code List<String>}） */
    public static final String METADATA_DEFAULT_PLAN = "defaultPlan";

    private final SchemaNodeDef node;

    private final RefResolver refResolver;

    private final LlmPayloadParser payloadParser;

    public HumanConfirmNodeAction(SchemaNodeDef node, RefResolver refResolver, LlmPayloadParser payloadParser) {
        this.node = node;
        this.refResolver = refResolver;
        this.payloadParser = payloadParser;
    }

    /**
     * 节点执行前的中断判定：清单未确认且有可确认内容时中断本次执行。
     *
     * <p>{@code nodeId} 由框架传入（即画布节点 id），与 {@link #apply} 写入的状态键前缀一致。
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        if (state.value(confirmedPlanKey()).isPresent()) {
            log.debug("[interrupt][清单已确认，不中断] nodeId={}", nodeId);
            return Optional.empty();
        }
        if (config.metadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY).isPresent()) {
            log.info("[interrupt][已携带人工反馈，不重复中断] nodeId={}", nodeId);
            return Optional.empty();
        }
        List<PlanItemVO> plan = parsePlan(state);
        if (plan.isEmpty()) {
            log.warn("[interrupt][批改清单为空，直接放行] nodeId={}，请检查上游大模型节点输出", nodeId);
            return Optional.empty();
        }
        List<String> defaultPlan = plan.stream()
                .filter(item -> Boolean.TRUE.equals(item.getNeedGrading()))
                .map(PlanItemVO::getFilePath)
                .toList();
        log.info("[interrupt][等待人工确认] nodeId={}, planItems={}, defaultConfirmItems={}",
                nodeId, plan.size(), defaultPlan.size());
        return Optional.of(InterruptionMetadata.builder(nodeId, state)
                .addMetadata(METADATA_PLAN, plan)
                .addMetadata(METADATA_DEFAULT_PLAN, defaultPlan)
                .build());
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
        Object confirmed = state.value(confirmedPlanKey()).orElse(null);
        if (isFilled(confirmed)) {
            // 状态中已有清单（恢复执行时由 updateState 注入）：原样写回节点输出，继续执行下游
            log.info("[apply][人工确认清单已确认，直接放行] nodeId={}, confirmedCount={}",
                    node.getId(), sizeOf(confirmed));
            return CompletableFuture.completedFuture(Map.of(confirmedPlanKey(), confirmed));
        }
        // 未中断而直接执行到本节点（清单为空等情形）：按规划结果自动放行，保证下游有确定输入
        List<PlanItemVO> plan = parsePlan(state);
        List<String> autoPlan = plan.stream()
                .filter(item -> Boolean.TRUE.equals(item.getNeedGrading()))
                .map(PlanItemVO::getFilePath)
                .toList();
        log.info("[apply][人工确认节点自动放行] nodeId={}, planItems={}, autoConfirmItems={}",
                node.getId(), plan.size(), autoPlan.size());
        return CompletableFuture.completedFuture(Map.of(confirmedPlanKey(), autoPlan));
    }

    /** 解析规划节点产出的批改清单（引用 {@code plan} 输入） */
    private List<PlanItemVO> parsePlan(OverAllState state) {
        InputSlotDef planSlot = planInputSlot();
        if (planSlot == null) {
            log.warn("[parsePlan][节点未声明 plan 输入，清单为空] nodeId={}", node.getId());
            return new ArrayList<>();
        }
        Object raw = refResolver.resolveSlot(planSlot, WorkflowStateView.of(state.data()), PLAN_INPUT_NAME);
        List<PlanItemVO> plan = payloadParser.parsePlan(raw == null ? null : String.valueOf(raw));
        if (plan.isEmpty()) {
            log.warn("[parsePlan][批改清单为空，请检查上游大模型节点输出] nodeId={}", node.getId());
        }
        return plan;
    }

    private InputSlotDef planInputSlot() {
        if (node.getData() == null || node.getData().getInputs() == null
                || node.getData().getInputs().getInputParameters() == null) {
            return null;
        }
        for (InputParamDef param : node.getData().getInputs().getInputParameters()) {
            if (param != null && PLAN_INPUT_NAME.equals(param.getName())) {
                return param.getInput();
            }
        }
        return null;
    }

    /** 确认清单的状态键：{nodeId}.{outputName} */
    private String confirmedPlanKey() {
        return RefResolver.stateKey(node.getId(), resolveOutputName());
    }

    private String resolveOutputName() {
        if (node.getData() != null && node.getData().getOutputs() != null) {
            for (OutputParamDef output : node.getData().getOutputs()) {
                if (output != null && output.getName() != null && !output.getName().isBlank()) {
                    return output.getName();
                }
            }
        }
        return DEFAULT_OUTPUT_NAME;
    }

    private boolean isFilled(Object value) {
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return value != null;
    }

    private int sizeOf(Object value) {
        return value instanceof Collection<?> collection ? collection.size() : 1;
    }

}
