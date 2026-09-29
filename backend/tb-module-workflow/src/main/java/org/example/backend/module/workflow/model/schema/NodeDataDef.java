package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点数据：元信息、输入声明、输出声明。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeDataDef {

    /** 节点元信息（标题 / 描述 / 图标） */
    private NodeMetaDef nodeMeta;

    /** 节点输入（输入参数、大模型参数、循环配置、结束节点内容） */
    private NodeInputsDef inputs;

    /** 节点输出声明 */
    private List<OutputParamDef> outputs = new ArrayList<>();

    /** schema 版本（如 "3"），当前仅作保留字段 */
    private String version;

}
