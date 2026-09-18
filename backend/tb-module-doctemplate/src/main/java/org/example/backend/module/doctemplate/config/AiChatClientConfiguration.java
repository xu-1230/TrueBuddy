package org.example.backend.module.doctemplate.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档模板生成使用的 ChatClient（OpenAI 兼容对话模型）
 */
@Configuration
public class AiChatClientConfiguration {

    @Bean
    public ChatClient docTemplateChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

}
