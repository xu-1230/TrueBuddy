package org.example.backend.module.workflow.service.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 节点执行时可见的状态视图：图状态快照 + 循环内部绑定。
 *
 * <p>取值优先级：循环内部绑定（如循环节点为其子节点绑定的 {@code 100004.item}）→ 图状态
 * （键为 {@code {nodeId}.{outputName}}，以及开始节点输出的裸变量名）。
 *
 * <p>该视图只读，节点执行不会直接改写状态，而是通过返回值写回（由 graph 的 KeyStrategy 合并）。
 */
public final class WorkflowStateView {

    private final Map<String, Object> data;
    private final Map<String, Object> localBindings;
    private final WorkflowProgressListener progressListener;

    private WorkflowStateView(Map<String, Object> data, Map<String, Object> localBindings,
                              WorkflowProgressListener progressListener) {
        this.data = data == null ? Collections.emptyMap() : data;
        this.localBindings = localBindings == null ? Collections.emptyMap() : localBindings;
        this.progressListener = progressListener == null ? WorkflowProgressListener.NO_OP : progressListener;
    }

    public static WorkflowStateView of(Map<String, Object> data) {
        return new WorkflowStateView(data, Collections.emptyMap(), WorkflowProgressListener.NO_OP);
    }

    /** 追加循环内部绑定，返回新视图（不修改原视图） */
    public WorkflowStateView withBindings(Map<String, Object> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new HashMap<>(localBindings);
        merged.putAll(bindings);
        return new WorkflowStateView(data, merged, progressListener);
    }

    /** 挂载进度监听器，返回新视图（流式调用由节点包装器注入） */
    public WorkflowStateView withListener(WorkflowProgressListener listener) {
        if (listener == null || listener == progressListener) {
            return this;
        }
        return new WorkflowStateView(data, localBindings, listener);
    }

    /** 进度监听器（永不为 null，非流式调用为 NO_OP） */
    public WorkflowProgressListener progressListener() {
        return progressListener;
    }

    /** 按状态键查找取值（先查循环绑定，再查图状态） */
    public Optional<Object> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        if (localBindings.containsKey(key)) {
            return Optional.ofNullable(localBindings.get(key));
        }
        return Optional.ofNullable(data.get(key));
    }

    /** 图状态快照（只读语义，调用方不应修改） */
    public Map<String, Object> data() {
        return data;
    }

}
