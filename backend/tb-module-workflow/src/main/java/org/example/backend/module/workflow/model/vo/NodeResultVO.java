package org.example.backend.module.workflow.model.vo;

import lombok.Data;

/**
 * 节点执行结果（{@code GET /workflow/get_process} 的 {@code nodeResults} 元素）。
 *
 * <p>字段口径对齐 Coze 的执行状态响应，但统一为项目 camelCase 命名，且不做循环内部
 * 子节点的 {@code batch} 子项拆解（循环内的逐项结果通过结束节点输出与业务结果查看）。
 */
@Data
public class NodeResultVO {

    /** 节点状态：未执行 */
    public static final int STATUS_PENDING = 1;
    /** 节点状态：执行中 */
    public static final int STATUS_RUNNING = 2;
    /** 节点状态：执行成功 */
    public static final int STATUS_SUCCESS = 3;
    /** 节点状态：执行失败 */
    public static final int STATUS_FAILED = 4;

    /** 节点 id */
    private String nodeId;

    /** 节点标题 */
    private String nodeName;

    /** 节点类型：Start / LLM / Loop / HumanConfirm / End */
    private String nodeType;

    /** 节点状态：1=未执行，2=执行中，3=执行成功，4=执行失败 */
    private Integer nodeStatus = STATUS_PENDING;

    /** 失败原因（成功为空串） */
    private String errorInfo = "";

    /** 错误级别（预留，固定空串） */
    private String errorLevel = "";

    /** 节点输入（JSON 字符串，由节点声明的输入解析结果序列化） */
    private String input = "";

    /** 节点输出（JSON 字符串，取该节点写入状态的 {@code {nodeId}.*} 键值） */
    private String output = "";

    /** 节点耗时（人类可读，如 {@code 0.000s} / {@code 2s} / {@code 1m32s}） */
    private String nodeExeCost = "0.000s";

    /** 节点耗时（毫秒） */
    private Long nodeExeCostMs = 0L;

    /** 是否需要异步（预留，固定 false） */
    private Boolean needAsync = Boolean.FALSE;

    /** 日志版本（固定 1） */
    private Integer logVersion = 1;

}
