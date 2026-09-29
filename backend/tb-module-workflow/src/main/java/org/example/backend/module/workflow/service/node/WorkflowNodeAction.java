package org.example.backend.module.workflow.service.node;

import org.example.backend.module.workflow.model.schema.SchemaNodeDef;
import org.example.backend.module.workflow.service.graph.WorkflowStateView;

import java.util.Map;

/**
 * 节点执行动作：把画布节点语义落到「读取状态 → 产出输出变量」。
 *
 * <p>返回值键为 {@code {nodeId}.{outputName}}，由 graph 的 KeyStrategy 合并进状态。
 * 该抽象同时被顶层图节点与循环内部子节点复用，避免两套执行逻辑。
 */
public interface WorkflowNodeAction {

    /** 对应的画布节点定义 */
    SchemaNodeDef node();

    /**
     * 执行节点。
     *
     * @param state 只读状态视图（含循环内部绑定）
     * @return 输出变量（键为 {@code {nodeId}.{outputName}}）
     */
    Map<String, Object> execute(WorkflowStateView state);

}
