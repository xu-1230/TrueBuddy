package org.example.textflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点业务数据（对应 text.json 中节点的 data 字段）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeData {

    /** 节点展示名称，如「方案规划」 */
    private String label;

    private String system;

    private String user;

    /** 输入变量定义 */
    private List<NodeField> inputs = new ArrayList<>();

    private List<NodeField> variableParameters = new ArrayList<>();
    /** 输出变量定义 */
    private List<NodeField> outputs = new ArrayList<>();

}
