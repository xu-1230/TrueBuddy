package org.example.backend.module.knowledgebase.service.parse.docmind;

import com.aliyun.docmind_api20220711.Client;
import com.aliyun.docmind_api20220711.models.GetDocParserResultRequest;
import com.aliyun.docmind_api20220711.models.QueryDocParserStatusRequest;
import com.aliyun.docmind_api20220711.models.SubmitDocParserJobAdvanceRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.knowledgebase.config.DocMindProperties;
import org.example.backend.module.knowledgebase.service.parse.DocumentParseGateway;
import org.example.backend.module.knowledgebase.service.parse.ParseTaskStatus;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 阿里云文档智能「文档解析（大模型版）」网关。
 *
 * <p>对应三个异步接口：
 * <ol>
 *     <li>{@code SubmitDocParserJobAdvance} —— 本地文件流提交，返回业务订单号（jobId）</li>
 *     <li>{@code QueryDocParserStatus} —— 轮询解析状态与进度</li>
 *     <li>{@code GetDocParserResult} —— 分页拉取版面信息（layouts），拼接为 Markdown</li>
 * </ol>
 *
 * <p>说明：MinIO 为内网地址，云端无法回拉，因此统一使用 Advance（FileUrlObject）方式直传文件流。
 */
@Slf4j
public class DocMindParseGateway implements DocumentParseGateway {

    /** 分页拉取的版面块数量安全上限，防止异常响应导致死循环 */
    private static final int MAX_LAYOUTS = 200_000;

    /** 接口限制：LayoutStepSize 取值 1~3000 */
    private static final int MAX_LAYOUT_STEP_SIZE = 3000;

    private final DocMindProperties properties;
    private final Client client;

    public DocMindParseGateway(DocMindProperties properties) throws Exception {
        this.properties = properties;
        Config config = new Config()
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret())
                .setEndpoint(properties.getEndpoint());
        this.client = new Client(config);
    }

    @Override
    public String submit(InputStream inputStream, String fileName) {
        boolean llmEnhancement = properties.isLlmEnhancement();
        SubmitDocParserJobAdvanceRequest request = new SubmitDocParserJobAdvanceRequest()
                .setFileUrlObject(inputStream)
                .setFileName(fileName)
                .setLlmEnhancement(llmEnhancement)
                .setFormulaEnhancement(properties.isFormulaEnhancement());
        if (StringUtils.hasText(properties.getEnhancementMode())) {
            request.setEnhancementMode(properties.getEnhancementMode());
        }
        // 接口约束：OutputHtmlTable 必须与 LlmEnhancement=true 同时开启，否则提交报「Parameter is invalid」
        if (properties.isOutputHtmlTable()) {
            if (llmEnhancement) {
                // 表格类型版面块额外返回 HTML 版正文（存放于该块的 llmResult 字段），避免合并单元格等复杂结构失真
                request.setOutputHtmlTable(true);
            } else {
                log.warn("已开启 output-html-table 但 llm-enhancement=false，接口不允许该组合，本次不请求 HTML 表格" + "（如需 HTML 表格请同时设置 DOCMIND_LLM_ENHANCEMENT=true）: fileName={}", fileName);
            }
        }
        try {
            var response = client.submitDocParserJobAdvance(request, runtimeOptions());
            var body = response.getBody();
            if (body == null || body.getData() == null || !StringUtils.hasText(body.getData().getId())) {
                String message = body == null ? "无响应" : body.getMessage();
                throw new ServiceException(500, "文档智能提交失败: " + message);
            }
            String jobId = body.getData().getId();
            log.info("文档智能任务已提交: fileName={}, jobId={}", fileName, jobId);
            return jobId;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档智能任务提交异常: fileName={}, error={}", fileName, e.getMessage(), e);
            throw new ServiceException(500, "文档智能任务提交失败: " + e.getMessage());
        }
    }

    @Override
    public ParseTaskStatus query(String jobId) {
        try {
            var response = client.queryDocParserStatusWithOptions(
                    new QueryDocParserStatusRequest().setId(jobId), runtimeOptions());
            var body = response.getBody();
            if (body == null || body.getData() == null) {
                return ParseTaskStatus.pending();
            }
            var data = body.getData();
            String status = asString(data.getStatus()).toLowerCase(Locale.ROOT);
            return switch (status) {
                case "success" -> {
                    ParseTaskStatus result = ParseTaskStatus.success();
                    result.setPageCount(data.getPageCountEstimate());
                    result.setTableCount(data.getTableCount());
                    result.setImageCount(data.getImageCount());
                    yield result;
                }
                case "fail", "failed" -> ParseTaskStatus.failed(
                        StringUtils.hasText(body.getMessage()) ? body.getMessage() : "云端文档解析失败");
                case "processing" -> ParseTaskStatus.processing(
                        data.getProcessing() == null ? 0 : Math.round(data.getProcessing()));
                default -> ParseTaskStatus.pending();
            };
        } catch (Exception e) {
            log.error("文档智能状态查询异常: jobId={}, error={}", jobId, e.getMessage(), e);
            throw new ServiceException(500, "文档智能状态查询失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<?, ?>> fetchMarkdown(String jobId) {
        int stepSize = Math.max(1, Math.min(MAX_LAYOUT_STEP_SIZE, properties.getLayoutStepSize()));
        List<Map<?, ?>> layouts = new ArrayList<>();
        int offset = 0;
        while (offset < MAX_LAYOUTS) {
            List<?> page = fetchLayoutPage(jobId, offset, stepSize);
            if (page == null || page.isEmpty()) {
                break;
            }
            for (Object item : page) {
                if (item instanceof Map<?, ?> map) {
                    layouts.add(map);
                }
            }
            if (page.size() < stepSize) {
                break;
            }
            offset += stepSize;
        }
//        if (layouts.isEmpty()) {
//            log.warn("文档智能返回结果为空: jobId={}", jobId);
//            return "";
//        }
//        String markdown = buildMarkdown(layouts);
//        log.info("文档智能结果拉取完成: jobId={}, layouts={}, markdownLength={}", jobId, layouts.size(), markdown.length());
        return layouts;
    }

    /** 拉取一页版面块 */
    private List<?> fetchLayoutPage(String jobId, int offset, int stepSize) {
        GetDocParserResultRequest request = new GetDocParserResultRequest()
                .setId(jobId)
                .setLayoutNum(offset)
                .setLayoutStepSize(stepSize);
        try {
            var response = client.getDocParserResultWithOptions(request, runtimeOptions());
            var body = response.getBody();
            if (body == null) {
                return null;
            }
            Map<String, ?> data = body.getData();
            if (data == null) {
                return null;
            }
            Object value = data.get("layouts");
            if (value == null) {
                value = data.get("Layouts");
            }
            return value instanceof List<?> list ? list : null;
        } catch (Exception e) {
            log.error("文档智能结果拉取异常: jobId={}, offset={}, error={}", jobId, offset, e.getMessage(), e);
            throw new ServiceException(500, "文档智能结果拉取失败: " + e.getMessage());
        }
    }

    /**
     * 将版面块按阅读顺序（index）拼接为 Markdown：
     * 优先使用版面块自带的 markdownContent，缺失时按 type/level 降级为纯文本 / 标题。
     */
    private String buildMarkdown(List<Map<?, ?>> layouts) {
        // 先按页码、再按页内阅读顺序排序（index 为版面阅读顺序，单独使用在多页文档中会错序）
        layouts.sort(Comparator
                .comparingInt((Map<?, ?> layout) -> asInt(layout.get("pageNum")))
                .thenComparingInt(layout -> asInt(layout.get("index"))));
        StringBuilder builder = new StringBuilder();
        for (Map<?, ?> layout : layouts) {
            String markdown = asString(layout.get("markdownContent"));
            if (!StringUtils.hasText(markdown)) {
                markdown = fallbackMarkdown(layout);
            }
            if (!StringUtils.hasText(markdown)) {
                continue;
            }
            builder.append(markdown.strip()).append("\n\n");
        }
        return builder.toString().strip();
    }

    private String fallbackMarkdown(Map<?, ?> layout) {
        String text = asString(layout.get("text"));
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String type = asString(layout.get("type")).toLowerCase(Locale.ROOT);
        if ("title".equals(type)) {
            int level = Math.min(6, Math.max(1, asInt(layout.get("level"))));
            return "#".repeat(level) + " " + text.strip();
        }
        return text.strip();
    }

    private RuntimeOptions runtimeOptions() {
        RuntimeOptions runtime = new RuntimeOptions();
        runtime.setConnectTimeout(properties.getConnectTimeoutMillis());
        runtime.setReadTimeout(properties.getReadTimeoutMillis());
        return runtime;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
