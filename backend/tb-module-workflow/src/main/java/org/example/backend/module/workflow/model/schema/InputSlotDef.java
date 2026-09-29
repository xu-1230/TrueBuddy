package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 取值槽位：声明类型（{@code type}）、可选元素结构（{@code schema}）与取值方式（{@code value}）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputSlotDef {

    /** 类型：string / integer / float / boolean / list / object / file */
    private String type;

    /** 当 type = list 时，元素的结构声明（如 {@code {"type": "string"}}） */
    private Map<String, Object> schema;

    /** 取值方式：ref（引用上游节点输出）/ literal（字面量） */
    private SlotValueDef value;

    /** 说明（开始节点输出声明中使用） */
    private String description;

    /** 是否必填（开始节点输出声明中使用） */
    private Boolean required;

    /** 默认值（开始节点输出声明中使用） */
    private String defaultValue;

}
