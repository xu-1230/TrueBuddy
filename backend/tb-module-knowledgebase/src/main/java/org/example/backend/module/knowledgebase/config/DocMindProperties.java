package org.example.backend.module.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云文档智能（文档解析大模型版）配置。
 *
 * <p>AK/SK 一律通过环境变量注入（缺失时启动即失败），禁止写入 application.yml。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tb.docmind")
public class DocMindProperties {
    /** 服务接入地址（IPv6 使用 docmind-api-dualstack.cn-hangzhou.aliyuncs.com） */
    private String endpoint = "docmind-api.cn-hangzhou.aliyuncs.com";

    /** AccessKey ID（环境变量 DOCMIND_ACCESS_KEY_ID） */
    private String accessKeyId;

    /** AccessKey Secret（环境变量 DOCMIND_ACCESS_KEY_SECRET） */
    private String accessKeySecret;

    /** 大模型增强：开启后耗时与费用上升（PPT、图表场景效果更好） */
    private boolean llmEnhancement = false;

    /** 大模型增强模式，仅 llm-enhancement=true 时生效，可填 VLM */
    private String enhancementMode;

    /** 公式识别增强 */
    private boolean formulaEnhancement = false;

    /** 通过 GetDocParserResult 分页拉取结果时的步长（接口上限 3000） */
    private int layoutStepSize = 3000;

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 20000;

    /** 读取超时（毫秒）：解析结果可能较大，留足时间 */
    private int readTimeoutMillis = 300000;

    /**
     * 返回缺失的必填配置项名称，为空表示配置完整。
     * 方法名刻意不使用 is/get 前缀，避免被 Spring Boot 绑定当作配置项。
     */
    public List<String> missingCredentials() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(accessKeyId)) {
            missing.add("DOCMIND_ACCESS_KEY_ID");
        }
        if (!StringUtils.hasText(accessKeySecret)) {
            missing.add("DOCMIND_ACCESS_KEY_SECRET");
        }
        return missing;
    }

    public boolean isLlmEnhancement() {
        return llmEnhancement;
    }

    public boolean isFormulaEnhancement() {
        return formulaEnhancement;
    }
}
