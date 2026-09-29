package org.example.backend.module.workflow.service.graph;

import java.util.Arrays;
import java.util.List;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_NODE_TYPE_UNSUPPORTED;

/**
 * 画布节点类型：以 Coze 风格编码为主，同时兼容语义名（前端 Vue-Flow 的 {@code type}）。
 */
public enum WorkflowNodeType {

    /** 开始节点：仅声明工作流输入，编译时映射为图的 START */
    START("开始", "1", "start", "input"),

    /** 结束节点：仅聚合最终输出，编译时映射为图的 END */
    END("结束", "2", "end", "output"),

    /** 大模型节点 */
    LLM("大模型", "3", "llm"),

    /** 循环节点：内部按列表 / 次数迭代执行 blocks */
    LOOP("循环", "21", "iteration", "loop"),

    /** 人工确认节点：执行到此暂停，等待调用方提交确认清单后继续（见 {@code HumanConfirmNodeAction}） */
    HUMAN_CONFIRM("人工确认", "30", "human", "confirm", "question"),

    ;

    private final String label;
    private final List<String> codes;

    WorkflowNodeType(String label, String... codes) {
        this.label = label;
        this.codes = Arrays.asList(codes);
    }

    public String getLabel() {
        return label;
    }

    /** 规范编码（如 "1"），写入日志与错误提示时使用 */
    public String getCode() {
        return codes.get(0);
    }

    /** 按编码 / 语义名解析；未知类型返回 null */
    public static WorkflowNodeType of(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        String normalized = rawType.trim().toLowerCase();
        for (WorkflowNodeType type : values()) {
            if (type.codes.contains(normalized)) {
                return type;
            }
        }
        return null;
    }

    /** 按编码 / 语义名解析；未知类型抛业务异常 */
    public static WorkflowNodeType require(String rawType) {
        WorkflowNodeType type = of(rawType);
        if (type == null) {
            throw exception(WORKFLOW_NODE_TYPE_UNSUPPORTED, String.valueOf(rawType));
        }
        return type;
    }

}
