package org.example.backend.module.workflow.model.vo;

import lombok.Data;

/**
 * 工作流保存请求：{@code id} 为空表示新建，非空表示更新已有工作流。
 */
@Data
public class WorkflowSaveReqVO {

    /** 工作流 id（空 = 新建） */
    private Long id;

    /** 工作流名称（为空时后端兜底为「未命名工作流」） */
    private String name;

    /** 工作流说明 */
    private String desc;

    /** 画布结构（JSON 字符串） */
    private String schemaJson;

    /** 状态：0=草稿，1=已发布（为空时按 0 处理） */
    private Integer status;

}
