package org.example.backend.module.doctemplate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档模板生成配置（前缀 tb.doctemplate）
 */
@Data
@Component
@ConfigurationProperties(prefix = "tb.doctemplate")
public class DocTemplateProperties {

    /** 生成文档临时目录（docx-mcp 进程需能读写该路径） */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/tb-doctemplate";

    /** 单次生成份数上限（LLM 规划结果超出时截断） */
    private Integer maxDocCount = 5;

    /** 提示词中正文段落清单的最大字符数（超出会截断并记 WARN，静默截断会放大漏改） */
    private Integer maxTextChars = 40000;

    /** 提示词中表格段落清单的最大字符数（含嵌套子表；超出会截断并记 WARN） */
    private Integer maxDigestChars = 60000;

    /** 自检未通过时「补规则 → 重落盘」的最大轮次 */
    private Integer maxFixRounds = 2;

    /** 严格自检：旧主题关键词仍有残留时把任务标记为「部分成功」 */
    private Boolean strictSelfCheck = true;

}
