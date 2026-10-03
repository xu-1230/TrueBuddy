package org.example.textflow.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流大模型节点（type = 3）使用的 ChatClient（OpenAI 兼容对话模型）。
 * <p>类名与 doctemplate 模块的配置类区分开：Spring 默认按「简单类名」生成 bean 名，
 * 同名配置类会被扫描成同一个 bean 名而启动失败（ConflictingBeanDefinitionException）。
 */
@Configuration
public class WorkflowChatClientConfiguration {

    @Bean
    public ChatClient nodeChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

}
