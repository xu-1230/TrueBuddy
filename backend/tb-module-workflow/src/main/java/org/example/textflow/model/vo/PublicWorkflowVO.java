package org.example.textflow.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 他人公有工作流的列表项出参（「公有库」浏览区，只读；fork 入口由共享链路提供）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PublicWorkflowVO extends WorkflowVO {

    /** 创建者显示名（昵称缺失降级账号名） */
    private String creatorName;

}
