package org.example.textflow.model.vo;

import lombok.Data;

/**
 * 工作流详情出参（`GET /workflow/detail`），含画布定义。
 */
@Data
public class WorkflowDetailRespVO {

    /** 工作流 id（雪花 ID 字符串） */
    private String id;

    /** 流程名称 */
    private String name;

    /** 流程说明 */
    private String description;

    /** 状态：0=草稿，1=已发布 */
    private Integer status;

    /** 可见性：0=私有，1=公有（画布页据此呈现「公有/私有」切换） */
    private Integer visibility;

    /** 当前登录用户是否为创建者（false 且非 builtin 时画布呈只读形态） */
    private Boolean mine;

    /** 是否为内置模板（前端据此分组「批改工作流 / 用户自定义工作流」） */
    private Boolean builtin;

    /** 顶层节点数（用于下拉里的「N 个步骤」提示） */
    private Integer stepCount;

    /** 画布定义 JSON 字符串 */
    private String schemaJson;

    /** 更新时间（{@code yyyy-MM-dd HH:mm:ss}） */
    private String updateTime;

}
