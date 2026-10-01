package org.example.textflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点输入/输出变量定义（对应 data.inputs、data.outputs 的元素）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeField {

    /** 变量名 */
    private String name;

    /** 变量类型：string / list / object 等 */
    private String type;

    /** 是否必填 */
    private Boolean required;

    /** 变量说明 */
    private String description;

    /** 复杂类型（如 list）的元素结构，例如 {"type": "string"} */
    private Map<String, Object> schema = new LinkedHashMap<>();

    /**
     * 输入变量的来源引用（仅 inputs 存在）：
     * {"type": "string", "blockID": "100001", "name": "cs"}。
     * <p>新版 inputs 元素只保留 name + input，变量类型也放在 input 里。
     */
    private NodeFieldRef input;

}
