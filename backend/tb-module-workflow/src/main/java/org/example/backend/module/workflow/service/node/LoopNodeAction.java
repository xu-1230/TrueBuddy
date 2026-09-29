package org.example.backend.module.workflow.service.node;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.workflow.config.WorkflowProperties;
import org.example.backend.module.workflow.model.schema.InputSlotDef;
import org.example.backend.module.workflow.model.schema.NodeInputsDef;
import org.example.backend.module.workflow.model.schema.OutputParamDef;
import org.example.backend.module.workflow.model.schema.SchemaNodeDef;
import org.example.backend.module.workflow.service.graph.RefResolver;
import org.example.backend.module.workflow.service.graph.WorkflowStateView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_RUN_FAILED;

/**
 * 循环节点：一次进入即遍历整份清单，「节点内部循环」执行子节点，并把每轮结果聚合为一个列表输出。
 *
 * <ul>
 *   <li>迭代来源：{@code loopType = array} 时按 {@code item} 输入列表；{@code count} 时按 {@code loopCount} 次数；</li>
 *   <li>循环变量：内部子节点通过 {@code {loopId}.item} 等绑定读取当前轮的值（引用解析器统一处理）；</li>
 *   <li>结果聚合：每轮取「输出声明 {@code input} 指向的引用」的值（如 {@code 100004-1.output}）；</li>
 *   <li>保护：条目数超过 {@code tb.workflow.max-loop-items} 时截断并告警。</li>
 * </ul>
 */
@Slf4j
public class LoopNodeAction implements WorkflowNodeAction {

    /** 循环当前元素的约定变量名 */
    private static final String ITEM_VARIABLE = "item";

    private static final String DEFAULT_RESULTS_NAME = "results";

    private final SchemaNodeDef node;

    /** 循环体内部子节点动作（按画布声明顺序） */
    private final List<WorkflowNodeAction> innerActions;

    private final RefResolver refResolver;

    private final WorkflowProperties properties;

    /** 并发执行器；为 null 时串行执行 */
    private final ExecutorService loopExecutor;

    public LoopNodeAction(SchemaNodeDef node, List<WorkflowNodeAction> innerActions, RefResolver refResolver,
                          WorkflowProperties properties, ExecutorService loopExecutor) {
        this.node = node;
        this.innerActions = innerActions;
        this.refResolver = refResolver;
        this.properties = properties;
        this.loopExecutor = loopExecutor;
    }

    @Override
    public SchemaNodeDef node() {
        return node;
    }

    @Override
    public Map<String, Object> execute(WorkflowStateView state) {
        NodeInputsDef inputs = node.getData() == null ? null : node.getData().getInputs();
        Map<String, Object> loopInputs = refResolver.resolveParams(inputs == null ? null : inputs.getInputParameters(), state);
        List<Object> items = resolveIterationItems(inputs, loopInputs, state);

        Map<String, Object> baseBindings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : loopInputs.entrySet()) {
            baseBindings.put(RefResolver.stateKey(node.getId(), entry.getKey()), entry.getValue());
        }

        List<Object> results = executeIterations(items, baseBindings, state);
        String resultsName = resolveResultsName();
        log.info("[execute][循环节点执行完成] nodeId={}, loopType={}, iterations={}, resultsName={}",
                node.getId(), inputs == null ? null : inputs.getLoopType(), results.size(), resultsName);
        // 结果集为空时也要通知一次，避免流式调用方一直停在「0/0」状态
        if (items.isEmpty()) {
            notifySafely(() -> state.progressListener().onLoopStart(node.getId(), 0));
        }
        return Map.of(RefResolver.stateKey(node.getId(), resultsName), results);
    }

    /** 解析迭代来源（列表 / 次数），并按上限截断 */
    private List<Object> resolveIterationItems(NodeInputsDef inputs, Map<String, Object> loopInputs, WorkflowStateView state) {
        List<Object> items = new ArrayList<>();
        String loopType = inputs == null ? null : inputs.getLoopType();
        if ("count".equalsIgnoreCase(loopType)) {
            InputSlotDef loopCount = inputs == null ? null : inputs.getLoopCount();
            int count = toInt(refResolver.resolveSlot(loopCount, state, "loopCount"), 0);
            for (int i = 1; i <= count; i++) {
                items.add(i);
            }
        } else {
            Object rawItems = loopInputs.get(ITEM_VARIABLE);
            if (rawItems == null) {
                rawItems = firstListValue(loopInputs);
            }
            if (rawItems instanceof List<?> list) {
                items.addAll(list);
            } else if (rawItems != null) {
                items.add(rawItems);
            }
        }
        int maxItems = Math.max(1, properties.getMaxLoopItems());
        if (items.size() > maxItems) {
            log.warn("[resolveIterationItems][循环条目数超过上限，已截断] nodeId={}, total={}, maxItems={}",
                    node.getId(), items.size(), maxItems);
            return new ArrayList<>(items.subList(0, maxItems));
        }
        if (items.isEmpty()) {
            log.warn("[resolveIterationItems][循环条目为空，不会执行循环体] nodeId={}, loopType={}", node.getId(), loopType);
        }
        return items;
    }

    private List<Object> executeIterations(List<Object> items, Map<String, Object> baseBindings, WorkflowStateView state) {
        int total = items.size();
        notifySafely(() -> state.progressListener().onLoopStart(node.getId(), total));
        int concurrency = Math.max(1, properties.getLoopConcurrency());
        if (loopExecutor == null || concurrency <= 1 || items.size() <= 1) {
            List<Object> results = new ArrayList<>(items.size());
            for (int index = 0; index < total; index++) {
                results.add(runOnce(items.get(index), index + 1, total, baseBindings, state));
            }
            return results;
        }
        List<Callable<Object>> tasks = new ArrayList<>(items.size());
        for (int index = 0; index < total; index++) {
            Object currentItem = items.get(index);
            int currentIndex = index + 1;
            tasks.add(() -> runOnce(currentItem, currentIndex, total, baseBindings, state));
        }
        try {
            // invokeAll 保证结果顺序与输入一致
            List<Future<Object>> futures = loopExecutor.invokeAll(tasks);
            List<Object> results = new ArrayList<>(futures.size());
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(WORKFLOW_RUN_FAILED, "循环执行被中断");
        } catch (ExecutionException e) {
            throw exception(WORKFLOW_RUN_FAILED, rootMessage(e));
        }
    }

    /** 执行一轮循环体，返回该轮聚合结果 */
    private Object runOnce(Object item, int index, int total, Map<String, Object> baseBindings,
                           WorkflowStateView state) {
        Map<String, Object> bindings = new LinkedHashMap<>(baseBindings);
        bindings.put(RefResolver.stateKey(node.getId(), ITEM_VARIABLE), item);
        WorkflowStateView iterationState = state.withBindings(bindings);

        Map<String, Object> iterationOutputs = new LinkedHashMap<>();
        for (WorkflowNodeAction inner : innerActions) {
            iterationOutputs.putAll(inner.execute(iterationState));
        }
        Object result = resolveIterationResult(iterationOutputs);
        // 每完成一条即回调：流式调用方据此「批改一份回传一份」
        notifySafely(() -> state.progressListener().onLoopItem(node.getId(), index, total, item, result));
        return result;
    }

    /** 进度回调失败（如客户端已断开）不应影响循环执行本身 */
    private void notifySafely(Runnable callback) {
        try {
            callback.run();
        } catch (Exception e) {
            log.warn("[notifySafely][进度回调失败，已忽略] nodeId={}, error={}", node.getId(), e.getMessage());
        }
    }

    /** 取本轮结果：优先按输出声明的引用取值，其次取唯一非空输出 */
    private Object resolveIterationResult(Map<String, Object> iterationOutputs) {
        OutputParamDef output = firstOutput();
        if (output != null && output.getInput() != null) {
            Object value = refResolver.resolveSlot(output.getInput(), WorkflowStateView.of(iterationOutputs), output.getName());
            if (value != null) {
                return refResolver.stringify(value);
            }
        }
        List<Object> values = iterationOutputs.values().stream().filter(value -> value != null).toList();
        if (values.size() == 1) {
            return refResolver.stringify(values.get(0));
        }
        if (values.isEmpty()) {
            log.warn("[resolveIterationResult][循环体未产出任何结果] nodeId={}", node.getId());
            return "";
        }
        return refResolver.stringify(iterationOutputs);
    }

    private OutputParamDef firstOutput() {
        if (node.getData() == null || node.getData().getOutputs() == null || node.getData().getOutputs().isEmpty()) {
            return null;
        }
        return node.getData().getOutputs().get(0);
    }

    private String resolveResultsName() {
        OutputParamDef output = firstOutput();
        if (output == null || output.getName() == null || output.getName().isBlank()) {
            return DEFAULT_RESULTS_NAME;
        }
        return output.getName();
    }

    private Object firstListValue(Map<String, Object> values) {
        for (Object value : values.values()) {
            if (value instanceof List<?>) {
                return value;
            }
        }
        return null;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

}
