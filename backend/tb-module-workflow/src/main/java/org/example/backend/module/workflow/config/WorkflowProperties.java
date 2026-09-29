package org.example.backend.module.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自定义工作流模块配置（prefix = {@code tb.workflow}）。
 *
 * <p>工作流定义已持久化到 workflow 表；执行侧的大模型与知识库检索仍为模拟实现。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tb.workflow")
public class WorkflowProperties {

    /** 会话有效期（分钟）：超时后确认接口会报「会话不存在或已过期」 */
    private int sessionTtlMinutes = 30;

    /** 循环节点并发度：1 表示串行；大于 1 时使用有界线程池并按输入顺序聚合结果 */
    private int loopConcurrency = 1;

    /** 单次循环最大条目数：超出部分截断并记录 WARN，防止画布配置失控 */
    private int maxLoopItems = 50;

    /** 模拟大模型单次调用延迟（毫秒）：便于观察真实链路耗时，设为 0 即不延迟 */
    private long mockLlmDelayMs = 30L;

}
