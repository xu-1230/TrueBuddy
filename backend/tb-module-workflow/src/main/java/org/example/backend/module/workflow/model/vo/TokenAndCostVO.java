package org.example.backend.module.workflow.model.vo;

import lombok.Data;

/**
 * token 与费用统计（{@code GET /workflow/get_process} 的 {@code tokenAndCost}）。
 *
 * <p>字段口径对齐 Coze 的执行状态响应；本项目未接入真实计费，固定返回零值占位，
 * 保留字段是为了与 Coze 契约同形，后续接入真实统计时无需改动调用方。
 */
@Data
public class TokenAndCostVO {

    private String inputTokens = "0 Tokens";

    private String outputTokens = "0 Tokens";

    private String totalTokens = "0 Tokens";

    private String inputCost = "0.00";

    private String outputCost = "0.00";

    private String totalCost = "0.00";

}
