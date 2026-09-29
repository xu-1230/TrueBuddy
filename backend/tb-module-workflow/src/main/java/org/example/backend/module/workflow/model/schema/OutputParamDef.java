package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 节点输出声明。
 *
 * <ul>
 *   <li>开始节点：{@code {name, type, required, schema, description, defaultValue}}，由外部入参提供；</li>
 *   <li>大模型节点：{@code {name, type}}，由节点执行结果写入；</li>
 *   <li>循环节点：额外带 {@code input}，声明输出由某个内部节点的输出聚合而来。</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OutputParamDef {

    /** 输出变量名 */
    private String name;

    /** 类型：string / integer / float / boolean / list / object */
    private String type;

    /** 是否必填（开始节点输出声明中使用） */
    private Boolean required;

    /** 当 type = list 时，元素的结构声明 */
    private Map<String, Object> schema;

    /** 说明 */
    private String description;

    /** 默认值（开始节点输出声明中使用） */
    private String defaultValue;

    /** 取值来源（循环节点输出使用） */
    private InputSlotDef input;

}
