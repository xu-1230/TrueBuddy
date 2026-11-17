package org.example.backend.module.homework.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 自定义导出列定义（homework_task.custom_fields 的元素，也是 /homework/grade 的请求元素）。
 *
 * <p>{@code key} 就是变量名：它同时是 custom 对象里的键、成绩卡字段名与导出表头；不再有
 * 独立的 {@code label}。{@code description} 会进入提示词，{@code type} 复用工作流变量类型
 * （string / integer / number / boolean / time / object / list / file），{@code schema.type}
 * 是 list / file 的元素类型。每一列都由模型逐学生产出，没有默认值 / 静态列。
 *
 * <p>{@code ignoreUnknown} 兼容早期存量脚本写过的 {@code default} / {@code label} 键（已废弃）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeworkCustomFieldVO {

    /** 变量名 / custom 对象里的键 / 成绩卡字段名 / 导出表头（不得与保留键冲突） */
    private String key;

    /** 给模型的定义描述（必填，告诉模型这一列写什么） */
    private String description;

    /** 工作流变量类型：string / integer / number / boolean / time / object / list / file */
    private String type;

    /** 容器类型的元素结构，例如 {"type": "string"}；list / file 必需 */
    private Map<String, Object> schema;

}
