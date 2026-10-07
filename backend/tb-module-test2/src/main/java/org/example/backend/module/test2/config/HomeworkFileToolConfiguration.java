package org.example.backend.module.test2.config;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.test2.tool.HomeworkFileTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作业文件读取工具注册。
 *
 * <p>把 {@link HomeworkFileTools} 上标注 {@code @Tool} 的方法注册成 Spring AI 的
 * {@link ToolCallbackProvider}：调用方可从中按工具名取出 {@link ToolCallback} 使用，
 * 与 docx-mcp 经 MCP Client 暴露的远程工具走同一套 ToolCallback 机制。
 *
 * <p><b>注意：provider bean 不会自动进入 ChatClient。</b>本项目的 {@code nodeChatClient} 是
 * {@code ChatClient.builder(chatModel).build()}（未挂工具），且 spring-ai 2.0.0-M3 的
 * {@code ChatClientBuilderConfigurer} 只应用 {@code ChatClientCustomizer}。
 * 把工具挂到批改节点（如 {@code .defaultTools(...)} 或按名取 {@link ToolCallback}）属于批改流程接入的内容。
 *
 * <p><b>这是进程内工具</b>（不经过 MCP 协议）。若需要让外部 MCP 宿主（Claude Desktop / Cursor 等）
 * 调用，再加 {@code spring-ai-starter-mcp-server-webmvc}，把同一个工具暴露为 MCP server 即可，工具实现不变。
 */
@Slf4j
@Configuration
public class HomeworkFileToolConfiguration {

    @Bean
    public ToolCallbackProvider homeworkFileToolCallbackProvider(HomeworkFileTools homeworkFileTools) {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(homeworkFileTools)
                .build();
        // 启动即打出工具名与入参 schema：便于确认模型实际看到的工具形态
        for (ToolCallback callback : provider.getToolCallbacks()) {
            log.info("已注册 AI 工具: name={}, inputSchema={}",
                    callback.getToolDefinition().name(), callback.getToolDefinition().inputSchema());
        }
        return provider;
    }

}
