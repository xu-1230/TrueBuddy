package org.example.backend.module.lecture.ppt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PPT 讲稿生成配置（前缀 {@code tb.lecture.ppt}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tb.lecture.ppt")
public class PptProperties {

    /**
     * 语速常数：每秒朗读多少字，用于「目标时长 ↔ 目标字数」换算。
     * <p>放在后端唯一一处，避免前端也存一份导致两边漂移。
     */
    private Double charsPerSecond = 4.0;

    /**
     * 每次模型调用的页数（后端按批循环）。
     * <p>为什么必须分批：模型输出有 token 上限，整篇几十页的讲稿一次性输出会被截断，
     * 截断后 JSON 不完整、整批作废。
     */
    private Integer batchSize = 8;

    /**
     * 单次请求的页数上限（安全阀，防止误把上百页灌进来）。
     */
    private Integer maxPages = 200;

    /**
     * 单页讲稿的字数硬上限：模型偶尔会写飞，超出即截断。
     */
    private Integer maxRemarkChars = 1500;

    /**
     * 单页送进 prompt 的「正文 + 备注」字符上限，控制单次请求体积。
     */
    private Integer maxPageInputChars = 1200;

}
