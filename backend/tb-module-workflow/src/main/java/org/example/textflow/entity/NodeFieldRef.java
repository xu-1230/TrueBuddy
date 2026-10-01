package org.example.textflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 输入变量的来源引用（新版 inputs 元素里的 input 字段）。
 * <p>形如 {"type": "string", "blockID": "100001", "name": "cs"}：
 * blockID 是产出该变量的节点 id，name 是该节点上声明的变量名。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeFieldRef {

    /** 来源节点 id（画布上的 blockID） */
    private String blockID;

    /** 来源节点上的变量名 */
    private String name;

    /** 变量类型：string / list / object 等 */
    private String type;

}
