package org.example.textflow.model.vo;

import lombok.Data;

/**
 * 工作流列表项出参（`GET /workflow/list`），**不含**画布定义。
 */
@Data
public class WorkflowVO {

    /** 工作流 id（雪花 ID 字符串） */
    private String id;

    /** 流程名称 */
    private String name;

    /** 流程说明 */
    private String description;

    /** 状态：0=草稿，1=已发布 */
    private Integer status;

    /** 可见性：0=私有，1=公有（列表卡片据此渲染与切换） */
    private Integer visibility;

    /** 是否为内置模板（前端据此分组「批改工作流 / 用户自定义工作流」） */
    private Boolean builtin;

    /** 顶层节点数（用于下拉里的「N 个步骤」提示） */
    private Integer stepCount;

    /** 更新时间（{@code yyyy-MM-dd HH:mm:ss}） */
    private String updateTime;

}
