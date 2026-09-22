package org.example.backend.module.knowledgebase.service.parse;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.knowledgebase.config.DocMindProperties;
import org.example.backend.module.knowledgebase.service.parse.docmind.DocMindParseGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 解析引擎装配：统一使用阿里云文档智能（文档解析大模型版）。
 *
 * <p>AK/SK 为必填配置，缺失或客户端初始化失败时启动即报错，避免以未配置状态运行。
 */
@Slf4j
@Configuration
public class ParseGatewayConfiguration {

    @Bean
    public DocumentParseGateway documentParseGateway(DocMindProperties properties) {
        List<String> missing = properties.missingCredentials();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("阿里云文档智能未配置，缺失环境变量: " + String.join(", ", missing) + "；请在项目根目录 .env 中补齐后重启（模板见 .env.example）");
        }

        DocMindParseGateway gateway;
        try {
            gateway = new DocMindParseGateway(properties);
        } catch (Exception e) {
            throw new IllegalStateException("阿里云文档智能客户端初始化失败: " + e.getMessage(), e);
        }
        log.info("知识库文档解析引擎: 阿里云文档智能（文档解析大模型版）, endpoint={}, llmEnhancement={}, enhancementMode={}, outputHtmlTable={}",
                properties.getEndpoint(), properties.isLlmEnhancement(), properties.getEnhancementMode(),
                properties.isOutputHtmlTable() && properties.isLlmEnhancement());
        return gateway;
    }
}
