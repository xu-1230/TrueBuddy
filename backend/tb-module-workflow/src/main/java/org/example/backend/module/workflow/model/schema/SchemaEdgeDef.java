package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 画布连线：{@code {sourceNodeID, targetNodeID, sourcePortID?, targetPortID?}}。
 *
 * <p>兼容简化写法 {@code {source, target}}（前端 Vue-Flow 风格）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaEdgeDef {

    /** 源节点 id */
    private String sourceNodeID;

    /** 目标节点 id */
    private String targetNodeID;

    /** 源端口 id（如 loop-output / loop-function-inline-output），执行时忽略 */
    private String sourcePortID;

    /** 目标端口 id（如 loop-function-inline-input），执行时忽略 */
    private String targetPortID;

    /** 兼容字段：源节点 id */
    private String source;

    /** 兼容字段：目标节点 id */
    private String target;

    /** 源节点 id（优先 sourceNodeID，回退 source） */
    public String resolveSource() {
        return sourceNodeID != null ? sourceNodeID : source;
    }

    /** 目标节点 id（优先 targetNodeID，回退 target） */
    public String resolveTarget() {
        return targetNodeID != null ? targetNodeID : target;
    }

}
