package org.example.backend.module.test2.service;

import com.aliyun.docmind_api20220711.Client;
import com.aliyun.docmind_api20220711.models.GetDocParserResultRequest;
import com.aliyun.docmind_api20220711.models.GetDocStructureResultRequest;
import com.aliyun.docmind_api20220711.models.QueryDocParserStatusRequest;
import com.aliyun.docmind_api20220711.models.SubmitDocParserJobAdvanceRequest;
import com.aliyun.docmind_api20220711.models.SubmitDocStructureJobAdvanceRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.knowledgebase.config.DocMindProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 阿里云文档智能「原始返回结果」测试：直接调用 SDK 三个接口，把接口返回的原始
 * {@code Data}（未做任何加工、拼接、排序）原样吐出，用于核对云端返回结构。
 *
 * <p>接口链路：
 * <ol>
 *     <li>{@code SubmitDocParserJobAdvance} → 原始响应体（Data.Id 为业务订单号）</li>
 *     <li>{@code QueryDocParserStatus} → 每次轮询的原始响应体（Data.Status / Processing / ...）</li>
 *     <li>{@code GetDocParserResult} → 原始 Data（layouts 版面块数组）</li>
 * </ol>
 */
@Slf4j
@Service
public class DocMindRawTestService {

    /** 轮询间隔 */
    private static final long POLL_INTERVAL_MILLIS = 3000L;

    /** 解析总超时 */
    private static final long TIMEOUT_MILLIS = 180_000L;

    /** 接口限制：LayoutStepSize 取值 1~3000 */
    private static final int MAX_LAYOUT_STEP_SIZE = 3000;

    /** 分页安全上限 */
    private static final int MAX_LAYOUTS = 200_000;

    /** 官方 CreateTime 时间格式 */
    private static final DateTimeFormatter CREATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private DocMindProperties properties;

    /**
     * 调用三个接口并返回原始 Data。
     *
     * @param filePath        测试文件路径（为空使用默认教学大纲文件）
     * @param layoutNum       结果分页起始位置（默认 0）
     * @param layoutStepSize  单页块数（1~3000，默认 3000）
     * @return 三个接口的原始返回数据
     */
    public Map<String, Object> testRawResponse(String filePath, int layoutNum, int layoutStepSize) {
        List<String> missing = properties.missingCredentials();
        if (!missing.isEmpty()) {
            throw new ServiceException(500, "阿里云文档智能未配置，缺失: " + String.join(", ", missing));
        }
        Path path = TestFileResolver.resolve(filePath);
        int stepSize = Math.max(1, Math.min(MAX_LAYOUT_STEP_SIZE, layoutStepSize));
        int startOffset = Math.max(0, layoutNum);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engine", "aliyun-docmind");
        result.put("endpoint", properties.getEndpoint());
        result.put("filePath", path.toString());
        result.put("fileName", path.getFileName().toString());

        long start = System.currentTimeMillis();
        String jobId = null;
        try {
            Client client = new Client(new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret())
                    .setEndpoint(properties.getEndpoint()));

            byte[] bytes = Files.readAllBytes(path);
            result.put("fileSize", bytes.length);
            String fileName = path.getFileName().toString();
            log.info("【文档智能原始结果测试】开始: file={}, size={}B", fileName, bytes.length);

            // ① 提交任务：原始响应体
            SubmitDocParserJobAdvanceRequest submitRequest = new SubmitDocParserJobAdvanceRequest()
                    .setFileUrlObject(new ByteArrayInputStream(bytes))
                    .setFileName(fileName);
            var submitResponse = client.submitDocParserJobAdvance(submitRequest, runtimeOptions());
            var submitBody = submitResponse.getBody();
            result.put("submitResponse", submitBody == null ? null : submitBody.toMap());
            jobId = submitBody == null || submitBody.getData() == null ? null : submitBody.getData().getId();
            result.put("jobId", jobId);
            if (jobId == null) {
                result.put("error", "未获取到业务订单号 Id");
                return result;
            }
            log.info("【文档智能原始结果测试】任务已提交: jobId={}", jobId);

            // ② 轮询状态：保留每次调用的原始响应体
            List<Map<String, Object>> statusResponses = new ArrayList<>();
            long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_MILLIS);
                var statusResponse = client.queryDocParserStatusWithOptions(
                        new QueryDocParserStatusRequest().setId(jobId), runtimeOptions());
                var statusBody = statusResponse.getBody();
                statusResponses.add(statusBody == null ? Map.of() : statusBody.toMap());
                String status = statusBody == null || statusBody.getData() == null
                        ? "" : String.valueOf(statusBody.getData().getStatus()).toLowerCase(Locale.ROOT);
                if ("success".equals(status) || "fail".equals(status) || "failed".equals(status)) {
                    break;
                }
            }
            result.put("statusResponses", statusResponses);

            // ③ 拉取结果：保留原始 Data（layouts）
            List<Map<String, Object>> resultPages = new ArrayList<>();
            int offset = startOffset;
            while (offset < MAX_LAYOUTS) {
                var response = client.getDocParserResultWithOptions(new GetDocParserResultRequest()
                        .setId(jobId)
                        .setLayoutNum(offset)
                        .setLayoutStepSize(stepSize), runtimeOptions());
                var body = response.getBody();
                Map<String, ?> data = body == null ? null : body.getData();
                int layoutCount = extractLayouts(data).size();

                Map<String, Object> page = new LinkedHashMap<>();
                page.put("layoutNum", offset);
                page.put("layoutStepSize", stepSize);
                page.put("statusCode", response.getStatusCode());
                page.put("layoutCount", layoutCount);
                // 原始 Data：layouts 版面块数组，未经任何加工
                page.put("data", data);
                resultPages.add(page);

                if (layoutCount == 0 || layoutCount < stepSize) {
                    break;
                }
                offset += stepSize;
            }
            result.put("resultPages", resultPages);
            log.info("【文档智能原始结果测试】完成: jobId={}, pages={}", jobId, resultPages.size());
            return result;
        } catch (Exception e) {
            log.error("【文档智能原始结果测试】异常: {}", e.getMessage(), e);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return result;
        } finally {
            result.put("elapsedMs", System.currentTimeMillis() - start);
        }
    }

    /**
     * 按官方响应的包络结构返回解析结果（与 testfile/test3.json 一致）：
     * <pre>
     * {
     *   "Status": "Success", "RequestId": "...", "CreateTime": "yyyy-MM-dd HH:mm:ss",
     *   "Completed": true,
     *   "Data": { "processing": 100, "numberOfSuccessfulParsing": 32, "layouts": [ ... ], "status": "success" },
     *   "Id": "docmind-..."
     * }
     * </pre>
     *
     * <p>字段来源：{@code Id/RequestId/Data.layouts} 来自 GetDocParserResult；
     * {@code Data.processing/Data.numberOfSuccessfulParsing/Data.status} 来自 QueryDocParserStatus；
     * {@code Completed} 由终态推导。唯一非接口返回的是 {@code CreateTime}——文档智能结果查询接口
     * 不返回该字段，此处取本地提交时刻（格式 yyyy-MM-dd HH:mm:ss）。
     *
     * @param filePath       测试文件路径（为空使用默认教学大纲文件）
     * @param llmEnhancement 大模型增强（为空取配置值；开启后版面层级 level、子类型 subType 更丰富）
     * @param enhancementMode 增强模式，可填 VLM（为空取配置值）
     */
    public Map<String, Object> testOfficialResult(String filePath, Boolean llmEnhancement, String enhancementMode) {
        List<String> missing = properties.missingCredentials();
        if (!missing.isEmpty()) {
            throw new ServiceException(500, "阿里云文档智能未配置，缺失: " + String.join(", ", missing));
        }
        Path path = TestFileResolver.resolve(filePath);
        String fileName = path.getFileName().toString();

        Map<String, Object> result = new LinkedHashMap<>();
        String jobId = null;
        try {
            Client client = new Client(new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret())
                    .setEndpoint(properties.getEndpoint()));
            byte[] bytes = Files.readAllBytes(path);

            // ① 提交任务（提交时刻作为 CreateTime）
            LocalDateTime submittedAt = LocalDateTime.now();
            boolean useLlmEnhancement = llmEnhancement != null ? llmEnhancement : properties.isLlmEnhancement();
            String useEnhancementMode = StringUtils.hasText(enhancementMode)
                    ? enhancementMode : properties.getEnhancementMode();
            SubmitDocParserJobAdvanceRequest submitRequest = new SubmitDocParserJobAdvanceRequest()
                    .setFileUrlObject(new ByteArrayInputStream(bytes))
                    .setFileName(fileName)
                    .setLlmEnhancement(useLlmEnhancement);
            if (StringUtils.hasText(useEnhancementMode)) {
                submitRequest.setEnhancementMode(useEnhancementMode);
            }
            var submitResponse = client.submitDocParserJobAdvance(submitRequest, runtimeOptions());
            var submitBody = submitResponse.getBody();
            jobId = submitBody == null || submitBody.getData() == null ? null : submitBody.getData().getId();
            if (jobId == null) {
                throw new ServiceException(500, "未获取到业务订单号 Id");
            }
            log.info("【官方结构测试】任务已提交: jobId={}, llmEnhancement={}, enhancementMode={}",
                    jobId, useLlmEnhancement, useEnhancementMode);

            // ② 轮询状态直到终态
            String statusValue = "";
            Integer processing = null;
            Integer successfulParsing = null;
            long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_MILLIS);
                var statusResponse = client.queryDocParserStatusWithOptions(
                        new QueryDocParserStatusRequest().setId(jobId), runtimeOptions());
                var data = statusResponse.getBody() == null ? null : statusResponse.getBody().getData();
                if (data == null) {
                    continue;
                }
                statusValue = data.getStatus() == null ? "" : data.getStatus();
                processing = data.getProcessing() == null ? null : Math.round(data.getProcessing());
                successfulParsing = data.getNumberOfSuccessfulParsing();
                String lower = statusValue.toLowerCase(Locale.ROOT);
                if ("success".equals(lower) || "fail".equals(lower) || "failed".equals(lower)) {
                    break;
                }
            }
            if (!"success".equalsIgnoreCase(statusValue)) {
                throw new ServiceException(500, "解析未成功: Status=" + statusValue);
            }

            // ③ 分页拉取原始 layouts（不做任何加工）
            List<Object> layouts = new ArrayList<>();
            String requestId = null;
            int offset = 0;
            while (offset < MAX_LAYOUTS) {
                var response = client.getDocParserResultWithOptions(new GetDocParserResultRequest()
                        .setId(jobId)
                        .setLayoutNum(offset)
                        .setLayoutStepSize(MAX_LAYOUT_STEP_SIZE), runtimeOptions());
                var body = response.getBody();
                if (body == null) {
                    break;
                }
                requestId = body.getRequestId();
                List<?> page = extractLayouts(body.getData());
                layouts.addAll(page);
                if (page.size() < MAX_LAYOUT_STEP_SIZE) {
                    break;
                }
                offset += MAX_LAYOUT_STEP_SIZE;
            }

            // ④ 组装官方结构（键顺序与官方响应一致）
            result.put("Status", "Success");
            result.put("RequestId", requestId);
            result.put("CreateTime", CREATE_TIME_FORMATTER.format(submittedAt));
            result.put("Completed", true);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("processing", processing);
            data.put("numberOfSuccessfulParsing", successfulParsing);
            data.put("layouts", layouts);
            data.put("status", statusValue.toLowerCase(Locale.ROOT));
            result.put("Data", data);
            result.put("Id", jobId);
            log.info("【官方结构测试】完成: jobId={}, layouts={}", jobId, layouts.size());
            return result;
        } catch (ServiceException e) {
            log.error("【官方结构测试】失败: {}", e.getMessage(), e);
            return buildFailResult(jobId, e.getMessage());
        } catch (Exception e) {
            log.error("【官方结构测试】异常: {}", e.getMessage(), e);
            return buildFailResult(jobId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 「文档智能解析」原始结果测试（SubmitDocStructureJob + GetDocStructureResult）。
     *
     * <p>与「文档解析大模型版」不同，该链路的响应包络自带 {@code Status/Completed}，
     * 且 {@code Data} 同时包含进度与 layouts——用于核对官方 test3.json 的来源接口。
     */
    public Map<String, Object> testStructureResult(String filePath) {
        List<String> missing = properties.missingCredentials();
        if (!missing.isEmpty()) {
            throw new ServiceException(500, "阿里云文档智能未配置，缺失: " + String.join(", ", missing));
        }
        Path path = TestFileResolver.resolve(filePath);
        String fileName = path.getFileName().toString();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engine", "aliyun-docmind-structure");
        result.put("fileName", fileName);
        String jobId = null;
        try {
            Client client = new Client(new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret())
                    .setEndpoint(properties.getEndpoint()));
            byte[] bytes = Files.readAllBytes(path);

            // ① 提交：SubmitDocStructureJobAdvance
            var submitResponse = client.submitDocStructureJobAdvance(new SubmitDocStructureJobAdvanceRequest()
                    .setFileUrlObject(new ByteArrayInputStream(bytes))
                    .setFileName(fileName)
                    .setOutputFormat(List.of("markdown")), runtimeOptions());
            var submitBody = submitResponse.getBody();
            result.put("submitResponse", submitBody == null ? null : submitBody.toMap());
            jobId = submitBody == null || submitBody.getData() == null ? null : submitBody.getData().getId();
            result.put("jobId", jobId);
            if (jobId == null) {
                result.put("error", "未获取到业务订单号 Id");
                return result;
            }
            log.info("【文档智能解析测试】任务已提交: jobId={}", jobId);

            // ② 轮询：GetDocStructureResult（Completed=true 即结束）
            List<Map<String, Object>> polls = new ArrayList<>();
            Map<String, Object> finalResponse = null;
            long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_MILLIS);
                var response = client.getDocStructureResultWithOptions(new GetDocStructureResultRequest()
                        .setId(jobId)
                        .setRevealMarkdown(true), runtimeOptions());
                var body = response.getBody();
                Map<String, Object> bodyMap = body == null ? Map.of() : body.toMap();
                polls.add(bodyMap);
                finalResponse = bodyMap;
                if (body != null && Boolean.TRUE.equals(body.getCompleted())) {
                    break;
                }
            }
            result.put("pollCount", polls.size());
            result.put("polls", polls);
            result.put("finalResponse", finalResponse);
            log.info("【文档智能解析测试】完成: jobId={}, polls={}", jobId, polls.size());
            return result;
        } catch (Exception e) {
            log.error("【文档智能解析测试】异常: {}", e.getMessage(), e);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return result;
        }
    }

    private Map<String, Object> buildFailResult(String jobId, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("Status", "Fail");
        result.put("Completed", true);
        result.put("Id", jobId);
        result.put("error", error);
        return result;
    }

    private List<?> extractLayouts(Map<String, ?> data) {
        if (data == null) {
            return List.of();
        }
        Object value = data.get("layouts");
        if (value == null) {
            value = data.get("Layouts");
        }
        return value instanceof List<?> list ? list : List.of();
    }

    private RuntimeOptions runtimeOptions() {
        RuntimeOptions runtime = new RuntimeOptions();
        runtime.setConnectTimeout(properties.getConnectTimeoutMillis());
        runtime.setReadTimeout(properties.getReadTimeoutMillis());
        return runtime;
    }
}
