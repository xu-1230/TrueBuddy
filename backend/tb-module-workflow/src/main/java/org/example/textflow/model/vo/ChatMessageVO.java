package org.example.textflow.model.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一轮交互的用户输入（{@link ChatReqVO#getMessage()}）。
 *
 * <p>续跑提交表单卡片时形如：
 * <pre>
 * {
 *   "type": "card_submit",
 *   "nodeId": "100003",
 *   "values": { "files": [ { "name": "第三章习题.docx", "path": "…", "size": 20480, "type": "…" } ] }
 * }
 * </pre>
 * {@code values} 是**结构化 JSON**（不学 Coze 把表单拼成「标签:值」文本），key 对应中断节点
 * {@code data.outputs} 里声明的变量名，后端可直接按 {@code state[节点id][变量名]} 回写状态。
 */
@Data
public class ChatMessageVO {

    /** 消息类型：{@code card_submit}（表单卡片提交） */
    private String type;

    /** 提交的是哪个中断节点的卡片（可选，仅用于与中断帧对齐校验） */
    private String nodeId;

    /** 表单字段取值：字段名 → 取值 */
    private Map<String, Object> values = new LinkedHashMap<>();

}
