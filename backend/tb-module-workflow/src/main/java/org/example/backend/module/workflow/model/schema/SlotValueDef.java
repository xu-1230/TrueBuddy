package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 槽位取值。
 *
 * <ul>
 *   <li>{@code type = literal}：{@code content} 为字面量（字符串 / 数字 / 布尔）；</li>
 *   <li>{@code type = ref}：{@code content} 为 {@code {blockID, name, source}} 形式的引用对象。</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlotValueDef {

    /** 取值方式：ref / literal */
    private String type;

    /** 字面量值，或引用对象（Map：blockID / name / source） */
    private Object content;

    /** 原始类型标记（{@code {type: 1}} 等），仅在缺少 type 时用于推断 */
    private Map<String, Object> rawMeta;

    /** 引用对象中的 blockID */
    public String refBlockId() {
        return refField("blockID");
    }

    /** 引用对象中的变量名 */
    public String refName() {
        return refField("name");
    }

    private String refField(String key) {
        if (!(content instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

}
