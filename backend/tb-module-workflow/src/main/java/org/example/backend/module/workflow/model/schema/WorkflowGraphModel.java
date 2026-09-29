package org.example.backend.module.workflow.model.schema;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 归一化后的工作流图模型：由 {@code SchemaNormalizer} 从画布 schema 产出，供 {@code SchemaGraphBuilder} 编译。
 */
@Data
public class WorkflowGraphModel {

    /** 图名称（取自工作流名称） */
    private String name;

    /** 开始节点（type = 1） */
    private SchemaNodeDef startNode;

    /** 结束节点（type = 2） */
    private SchemaNodeDef endNode;

    /** 需要编译为图节点的可执行节点（大模型 / 循环 / 人工确认），按画布声明顺序 */
    private List<SchemaNodeDef> executableNodes = new ArrayList<>();

    /** 全部节点索引（含开始 / 结束 / 循环内部子节点），键为节点 id */
    private Map<String, SchemaNodeDef> nodeIndex = new LinkedHashMap<>();

    /** 节点级连线（可能包含开始 / 结束节点 id） */
    private List<GraphNodeEdge> edges = new ArrayList<>();

    /** 按 id 取节点 */
    public SchemaNodeDef node(String nodeId) {
        return nodeIndex.get(nodeId);
    }

}
