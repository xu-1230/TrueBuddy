package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点输入定义。
 *
 * <ul>
 *   <li>{@code inputParameters}：业务输入，元素形如
 *       {@code {name, input: {type, value: {type: 'ref'|'literal', content}}}}；</li>
 *   <li>{@code llmParam}：大模型参数（prompt / model / maxTokens…），结构与 inputParameters 相同；</li>
 *   <li>{@code loopType} / {@code loopCount}：循环节点的迭代方式（{@code array} 按列表、{@code count} 按次数）；</li>
 *   <li>{@code content}：结束节点的输出内容模板（如 {@code {{results}}}）。</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeInputsDef {

    /** 业务输入参数 */
    private List<InputParamDef> inputParameters = new ArrayList<>();

    /** 大模型参数（prompt / modleName / maxTokens / responseFormat…） */
    private List<InputParamDef> llmParam = new ArrayList<>();

    /** 循环类型：array（按列表迭代）/ count（按次数迭代） */
    @JsonProperty("loopType")
    private String loopType;

    /** 循环次数（loopType = count 时生效） */
    private InputSlotDef loopCount;

    /** 循环内部临时变量（当前画布未使用） */
    private List<InputParamDef> variableParameters = new ArrayList<>();

    /** 结束节点：输出内容模板 */
    private InputSlotDef content;

}
