package org.example.textflow.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 工作流连线（对应 text.json 中 edges 数组的元素）。
 * <p>新版画布用 sourceNodeID / targetNodeID，旧版用 source / target，这里都兼容。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowEdge {

    /** 起点节点 id */
    @JsonAlias({"sourceNodeID", "sourceNodeId", "source"})
    private String source;

    /** 终点节点 id */
    @JsonAlias({"targetNodeID", "targetNodeId", "target"})
    private String target;

    /** 连线唯一标识：必填，全图（含迭代体内部边）唯一，约定 e-{source}-{target} */
    private String id;

}
