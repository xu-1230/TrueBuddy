package org.example.textflow.model.vo;

import lombok.Data;

/**
 * 保存工作流的入参（`POST /workflow/save`）。
 */
@Data
public class WorkflowSaveReqVO {

    /** 工作流 id：空 / null = 新建；非空 = 更新该记录（雪花 ID 字符串） */
    private String id;

    /** 流程名称（必填，≤100） */
    private String name;

    /** 流程说明（可选，≤500） */
    private String description;

    /** 画布定义 JSON 字符串：{@code {nodes, edges}}，含迭代节点内嵌的 blocks / edges */
    private String schemaJson;

}
