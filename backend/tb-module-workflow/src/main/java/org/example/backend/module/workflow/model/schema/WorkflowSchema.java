package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 画布结构：节点 / 边 / 版本标记。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowSchema {

    /** 画布节点（含开始、结束、循环内部子节点） */
    private List<SchemaNodeDef> nodes = new ArrayList<>();

    /** 画布连线 */
    private List<SchemaEdgeDef> edges = new ArrayList<>();

    /** 版本标记（如 {@code {"loop": "v2"}}），当前仅作保留字段 */
    private Map<String, Object> versions = new LinkedHashMap<>();

}
