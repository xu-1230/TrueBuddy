package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 画布节点定义。
 *
 * <p>{@code type} 为 Coze 风格编码（{@code 1} 开始、{@code 2} 结束、{@code 3} 大模型、
 * {@code 21} 循环、{@code 30} 人工确认），也兼容语义名（{@code input/llm/iteration/human/output}）。
 * 循环节点的内部子节点放在 {@code blocks} 中，内部连线放在 {@code edges} 中。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaNodeDef {

    /** 节点 id（图内唯一） */
    private String id;

    /** 节点类型编码 */
    private String type;

    /** 节点数据（元信息 / 输入 / 输出） */
    private NodeDataDef data;

    /** 循环节点的内部子节点；其余节点为空 */
    private List<SchemaNodeDef> blocks = new ArrayList<>();

    /** 循环节点的内部连线；其余节点为空 */
    private List<SchemaEdgeDef> edges = new ArrayList<>();

    /** 画布坐标等展示信息（{@code position} / {@code canvasPosition}），执行时不使用 */
    private Map<String, Object> meta;

    /** 节点标题（如「批改规划」），用于 mock 语义判定与日志 */
    public String title() {
        return data != null && data.getNodeMeta() != null ? data.getNodeMeta().getTitle() : null;
    }

}
