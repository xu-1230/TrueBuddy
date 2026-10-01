package org.example.textflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流编排定义（对应 resources/text.json 的根结构）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowProcessRespVO {

    /** 节点列表 */
    private List<WorkflowNode> nodes = new ArrayList<>();

    /** 连线（节点之间的流转关系） */
    private List<WorkflowEdge> edges = new ArrayList<>();

}
