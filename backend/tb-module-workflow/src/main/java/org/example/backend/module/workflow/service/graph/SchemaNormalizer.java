package org.example.backend.module.workflow.service.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.workflow.model.schema.GraphNodeEdge;
import org.example.backend.module.workflow.model.schema.SchemaEdgeDef;
import org.example.backend.module.workflow.model.schema.SchemaNodeDef;
import org.example.backend.module.workflow.model.schema.WorkflowDetailSchema;
import org.example.backend.module.workflow.model.schema.WorkflowGraphModel;
import org.example.backend.module.workflow.model.schema.WorkflowMetaSchema;
import org.example.backend.module.workflow.model.schema.WorkflowSchema;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_SCHEMA_EMPTY;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_SCHEMA_INVALID;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_SCHEMA_JSON_INVALID;

/**
 * 画布定义的加载与归一化：
 * <ol>
 *   <li>读取 JSON（兼容「外层详情对象」与「裸 schema」两种写法）；</li>
 *   <li>校验结构（单开始 / 单结束、节点类型合法、id 非空）；</li>
 *   <li>产出归一化图模型 {@link WorkflowGraphModel}。</li>
 * </ol>
 */
@Slf4j
@Component
public class SchemaNormalizer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 解析工作流定义 JSON（兼容裸 schema 写法：顶层直接是 nodes / edges） */
    public WorkflowDetailSchema readDetail(String json) {
        if (json == null || json.isBlank()) {
            throw exception(WORKFLOW_SCHEMA_EMPTY);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw exception(WORKFLOW_SCHEMA_JSON_INVALID, e.getMessage());
        }
        try {
            if (root.has("nodes")) {
                // 裸 schema：补齐外层结构，便于统一处理
                WorkflowSchema schema = objectMapper.treeToValue(root, WorkflowSchema.class);
                WorkflowMetaSchema meta = new WorkflowMetaSchema();
                meta.setName("未命名工作流");
                meta.setSchema(schema);
                WorkflowDetailSchema detail = new WorkflowDetailSchema();
                detail.setWorkflow(meta);
                return detail;
            }
            return objectMapper.treeToValue(root, WorkflowDetailSchema.class);
        } catch (Exception e) {
            throw exception(WORKFLOW_SCHEMA_INVALID, e.getMessage());
        }
    }

    /** 取出画布结构（{@code schema_json}） */
    public WorkflowSchema extractSchema(WorkflowDetailSchema detail) {
        if (detail == null || detail.getWorkflow() == null || detail.getWorkflow().getSchema() == null) {
            throw exception(WORKFLOW_SCHEMA_EMPTY);
        }
        return detail.getWorkflow().getSchema();
    }

    /** 归一化：校验结构并产出图模型 */
    public WorkflowGraphModel normalize(WorkflowSchema schema, String graphName) {
        if (schema == null || schema.getNodes() == null || schema.getNodes().isEmpty()) {
            throw exception(WORKFLOW_SCHEMA_EMPTY);
        }

        WorkflowGraphModel model = new WorkflowGraphModel();
        List<SchemaNodeDef> executableNodes = new ArrayList<>();
        Map<String, SchemaNodeDef> nodeIndex = new LinkedHashMap<>();

        for (SchemaNodeDef node : schema.getNodes()) {
            if (node == null) {
                continue;
            }
            if (node.getId() == null || node.getId().isBlank()) {
                throw exception(WORKFLOW_SCHEMA_INVALID, "存在缺少 id 的节点");
            }
            WorkflowNodeType type = WorkflowNodeType.require(node.getType());
            if (nodeIndex.containsKey(node.getId())) {
                throw exception(WORKFLOW_SCHEMA_INVALID, "节点 id 重复：" + node.getId());
            }
            nodeIndex.put(node.getId(), node);
            // 循环内部子节点一并入索引，供循环执行时查找
            if (node.getBlocks() != null) {
                for (SchemaNodeDef block : node.getBlocks()) {
                    if (block == null || block.getId() == null) {
                        continue;
                    }
                    WorkflowNodeType.require(block.getType());
                    nodeIndex.putIfAbsent(block.getId(), block);
                }
            }
            switch (type) {
                case START -> {
                    if (model.getStartNode() != null) {
                        throw exception(WORKFLOW_SCHEMA_INVALID, "存在多个开始节点");
                    }
                    model.setStartNode(node);
                }
                case END -> {
                    if (model.getEndNode() != null) {
                        throw exception(WORKFLOW_SCHEMA_INVALID, "存在多个结束节点");
                    }
                    model.setEndNode(node);
                }
                default -> executableNodes.add(node);
            }
        }
        if (model.getStartNode() == null) {
            throw exception(WORKFLOW_SCHEMA_INVALID, "缺少开始节点");
        }
        if (model.getEndNode() == null) {
            throw exception(WORKFLOW_SCHEMA_INVALID, "缺少结束节点");
        }

        List<GraphNodeEdge> edges = normalizeEdges(schema.getEdges(), nodeIndex);
        warnUnreachable(model.getStartNode().getId(), edges, executableNodes);

        model.setName(graphName == null || graphName.isBlank() ? "workflow-graph" : graphName);
        model.setExecutableNodes(executableNodes);
        model.setNodeIndex(nodeIndex);
        model.setEdges(edges);
        return model;
    }

    private List<GraphNodeEdge> normalizeEdges(List<SchemaEdgeDef> rawEdges, Map<String, SchemaNodeDef> nodeIndex) {
        List<GraphNodeEdge> edges = new ArrayList<>();
        if (rawEdges == null) {
            return edges;
        }
        Set<String> deduplicated = new HashSet<>();
        for (SchemaEdgeDef raw : rawEdges) {
            if (raw == null) {
                continue;
            }
            String source = raw.resolveSource();
            String target = raw.resolveTarget();
            if (source == null || target == null) {
                continue;
            }
            if (!nodeIndex.containsKey(source) || !nodeIndex.containsKey(target)) {
                log.warn("[normalizeEdges][忽略指向未知节点的连线] {} -> {}", source, target);
                continue;
            }
            if (deduplicated.add(source + "->" + target)) {
                edges.add(new GraphNodeEdge(source, target));
            }
        }
        return edges;
    }

    /** 可达性检查：仅告警不失败，避免画布存在孤立节点时无法保存/执行 */
    private void warnUnreachable(String startNodeId, List<GraphNodeEdge> edges, List<SchemaNodeDef> executableNodes) {
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (GraphNodeEdge edge : edges) {
            outgoing.computeIfAbsent(edge.source(), key -> new ArrayList<>()).add(edge.target());
        }
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!reached.add(current)) {
                continue;
            }
            for (String next : outgoing.getOrDefault(current, List.of())) {
                if (!reached.contains(next)) {
                    queue.add(next);
                }
            }
        }
        for (SchemaNodeDef node : executableNodes) {
            if (!reached.contains(node.getId())) {
                log.warn("[warnUnreachable][节点从开始节点不可达，执行时会被跳过] nodeId={}, title={}",
                        node.getId(), node.title());
            }
        }
    }

}
