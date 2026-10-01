package org.example.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TrueBuddy 后端启动类
 * <p>scanBasePackages 额外包含 {@code org.example.textflow}：工作流模块沿用 text-flow 的包名，
 * 不在 {@code org.example.backend} 之下，需显式声明才能被组件扫描到。
 */
@SpringBootApplication(scanBasePackages = {"org.example.backend", "org.example.textflow"})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
