package org.example.backend.module.infra.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置（前缀 minio）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** MinIO 服务地址，如 http://localhost:9000 */
    private String endpoint;
    /** 访问密钥 */
    private String accessKey;
    /** 密钥 */
    private String secretKey;
    /** 存储桶 */
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

}
