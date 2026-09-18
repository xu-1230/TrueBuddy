package org.example.backend.framework.web.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 组件配置项
 */
@ConfigurationProperties(prefix = "tb.web")
@Data
public class WebProperties {

    private Api appApi = new Api("/app-api");
    private Api adminApi = new Api("/admin-api");

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        private String prefix;
    }
}