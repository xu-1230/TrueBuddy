package org.example.backend.module.workflow.service.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.workflow.mock.MockLlmEngine;
import org.example.backend.module.workflow.model.schema.NodeInputsDef;
import org.example.backend.module.workflow.model.schema.OutputParamDef;
import org.example.backend.module.workflow.model.schema.SchemaNodeDef;
import org.example.backend.module.workflow.service.graph.RefResolver;
import org.example.backend.module.workflow.service.graph.WorkflowStateView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型节点：解析输入与参数 → 渲染提示词 → 调用模拟引擎 → 写出 {@code output} / {@code reasoning_content}。
 */
@Slf4j
@RequiredArgsConstructor
public class MockLlmNodeAction implements WorkflowNodeAction {

    private final SchemaNodeDef node;

    private final RefResolver refResolver;

    private final MockLlmEngine engine;

    @Override
    public SchemaNodeDef node() {
        return node;
    }

    @Override
    public Map<String, Object> execute(WorkflowStateView state) {
        NodeInputsDef inputs = node.getData() == null ? null : node.getData().getInputs();
        Map<String, Object> locals = refResolver.resolveParams(inputs == null ? null : inputs.getInputParameters(), state);
        Map<String, Object> llmParams = refResolver.resolveParams(inputs == null ? null : inputs.getLlmParam(), state);
        String prompt = refResolver.renderTemplate(asString(llmParams.get("prompt")), locals, state);

        MockLlmEngine.MockLlmResult result = engine.invoke(new MockLlmEngine.MockLlmRequest(
                node.getId(), node.title(), prompt, locals, llmParams));

        List<String> outputNames = declaredOutputNames();
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put(RefResolver.stateKey(node.getId(), outputNames.get(0)), result.output());
        if (outputNames.size() > 1) {
            outputs.put(RefResolver.stateKey(node.getId(), outputNames.get(1)), result.reasoning());
        }
        log.debug("[execute][大模型节点执行完成] nodeId={}, title={}, inputs={}, outputs={}",
                node.getId(), node.title(), locals.keySet(), outputs.keySet());
        return outputs;
    }

    /** 声明输出名；未声明时按约定使用 output / reasoning_content */
    private List<String> declaredOutputNames() {
        List<String> names = new ArrayList<>();
        if (node.getData() != null && node.getData().getOutputs() != null) {
            for (OutputParamDef output : node.getData().getOutputs()) {
                if (output != null && output.getName() != null && !output.getName().isBlank()) {
                    names.add(output.getName());
                }
            }
        }
        if (names.isEmpty()) {
            names.add("output");
            names.add("reasoning_content");
        }
        return names;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
