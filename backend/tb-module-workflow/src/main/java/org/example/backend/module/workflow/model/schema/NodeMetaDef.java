package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 节点元信息（用于展示与 mock 语义判定）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeMetaDef {

    /** 节点标题，如「开始」「批改规划」「人工确认」「逐项批改」「结束」 */
    private String title;

    /** 副标题，如「大模型」「循环」「中断」 */
    private String subTitle;

    /** 节点说明 */
    private String description;

    /** 图标地址 */
    private String icon;

    /** 主色（画布展示用） */
    private String mainColor;

}
