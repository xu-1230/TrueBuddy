package org.example.backend.module.workflow.model.schema;

/**
 * 归一化后的连线（源 / 目标节点 id）。
 *
 * @param source 源节点 id
 * @param target 目标节点 id
 */
public record GraphNodeEdge(String source, String target) {
}
