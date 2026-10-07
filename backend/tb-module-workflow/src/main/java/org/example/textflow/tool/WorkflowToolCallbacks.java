package org.example.textflow.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 工作流大模型节点可自主调用的工具集合（启动时确定一次）。
 *
 * <p>只挑白名单里的**进程内**工具：
 * <ul>
 *   <li>MCP 远程工具（docx-*）依赖独立子进程的会话与句柄，不适合让工作流节点自主调用，故不挂；</li>
 *   <li>白名单按工具名匹配，不依赖具体 bean 名，工具实现在哪个模块都能被收集到。</li>
 * </ul>
 *
 * <p>当前仅 {@code read_homework_file_text}（按 fileId 读作业文件文本，供逐份批改用）。
 */
@Component
public class WorkflowToolCallbacks {

    private static final Logger log = LoggerFactory.getLogger(WorkflowToolCallbacks.class);

    /** 允许工作流大模型节点调用的工具名 */
    private static final Set<String> ENABLED_TOOL_NAMES = Set.of("read_homework_file_text");

    private final List<ToolCallback> callbacks;

    public WorkflowToolCallbacks(ObjectProvider<ToolCallbackProvider> toolCallbackProviders) {
        this.callbacks = toolCallbackProviders.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .filter(callback -> callback != null && callback.getToolDefinition() != null
                        && ENABLED_TOOL_NAMES.contains(callback.getToolDefinition().name()))
                .toList();
        if (callbacks.isEmpty()) {
            log.warn("工作流大模型节点没有可用工具（白名单 {} 均未注册）", ENABLED_TOOL_NAMES);
        } else {
            log.info("工作流大模型节点可用工具: {}",
                    callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList());
        }
    }

    /** 装配图时传给大模型节点 */
    public List<ToolCallback> callbacks() {
        return callbacks;
    }

}
