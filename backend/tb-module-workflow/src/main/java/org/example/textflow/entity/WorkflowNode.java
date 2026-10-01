package org.example.textflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点（对应 text.json 中 nodes 数组的元素）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowNode {

    /** 节点唯一标识：新版为数字串（100001 / 132996 / 900001），旧版为语义化名称（start / llm-plan ...） */
    private String id;

    /** 节点类型（数字编码）：1=开始、2=结束、3=大模型、4=迭代、5=人工确认 */
    private String type;

    /** 画布坐标 */
    private WorkflowPosition position;

    /** 节点业务数据（名称、输入、输出定义） */
    private NodeData data;

    /** 节点内部的子块定义（新版字段，当前流程为空，按原始结构保留） */
    private List<Map<String, Object>> blocks = new ArrayList<>();

    /** 节点内部的连线（新版字段，当前流程为 null，按原始结构保留） */
    private List<Map<String, Object>> edges;

}
