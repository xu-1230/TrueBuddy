package org.example.backend.module.lecture.ppt.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PPT 讲稿生成使用的 ChatClient（OpenAI 兼容对话模型）。
 *
 * <p>与 {@code tb-module-doctemplate} 的 {@code docTemplateChatClient} 同构：模型配置
 * （base-url / model / temperature / retry）统一来自 {@code spring.ai.openai.*}，
 * 这里只负责给出一个按名注入的 ChatClient bean，避免两个模块互相拿到对方的 bean。
 */
@Configuration
public class PptChatClientConfiguration {

    @Bean
    public ChatClient pptChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

}
