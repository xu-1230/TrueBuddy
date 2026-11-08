package org.example.textflow.model.vo;

import lombok.Data;

import java.util.Map;

/**
 * {@code POST /chat} 入参。
 *
 * <p>首轮与续跑**共用同一个接口**，由服务端按「该会话最新一条 {@code workflow_run} 的状态」分流，
 * 客户端**不传状态**（传了既可伪造，也有「前端已收到中断帧、DB 落库还没提交」的时序竞态）：
 * <ul>
 *   <li>{@code conversationId} 对应的会话没有执行记录 → 新起一条执行；</li>
 *   <li>最新执行 {@code status = 4}（中断）→ 续跑，{@code message.values} 作人工反馈；</li>
 *   <li>最新执行已是终态（2 / 3 / 5）→ 新起一条执行；</li>
 *   <li>最新执行仍在执行中（0 / 1）→ 拒绝（同一会话不允许并发执行）。</li>
 * </ul>
 */
@Data
public class ChatReqVO {

    /** 会话编号（必填，由 {@code POST /chat/conversation} 下发） */
    private String conversationId;

    /** 工作流 id（可选）：带上时校验与会话绑定的流程一致，避免前端串会话 */
    private String workflowId;

    /**
     * **首轮的业务输入**：变量名 → 取值（如 {@code file_tree} / {@code description} / {@code know}），
     * 由服务端并入初始状态，开始节点按 {@code data.outputs} 声明的同名键**直读**——
     * required 变量缺失（取不到 / 空白 / 空列表）时整轮直接失败，没有演示兜底。
     * <p>续跑**忽略**它（续跑只认 {@code message.values}，否则会出现"两处都能送值、到底哪份算数"的歧义）；
     * 键名以 {@code __} 开头的一律跳过（{@code __START__} / {@code __human_feedback__} 是引擎保留键）。
     */
    private Map<String, Object> inputs;

    /** 本轮输入：首轮可省略（业务输入由 {@code inputs} 承载）；续跑必填，承载表单卡片的填写值 */
    private ChatMessageVO message;

}
