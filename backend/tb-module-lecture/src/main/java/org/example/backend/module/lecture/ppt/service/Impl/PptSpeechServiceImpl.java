package org.example.backend.module.lecture.ppt.service.Impl;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.ppt.config.PptProperties;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateRespVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeRespVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechPageVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechResultVO;
import org.example.backend.module.lecture.ppt.prompt.PptPromptConstants;
import org.example.backend.module.lecture.ppt.service.PptSpeechService;
import org.example.backend.module.lecture.ppt.util.SpeechScriptUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_SPEECH_GENERATE_FAILED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_SPEECH_NARRATE_FAILED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_SPEECH_NARRATE_INPUT_EMPTY;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_SPEECH_PAGES_EMPTY;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_SPEECH_PAGES_TOO_MANY;

/**
 * 讲稿生成实现。
 *
 * <p>无状态：文档在前端（本地导入的 PPT），这里只做「收页 → 分批问模型 → 按页回填」。
 * 分批与补跑是本类的核心，理由见 {@link PptProperties#getBatchSize()}：
 * 模型输出有 token 上限，整篇一次问会被截断，而截断后 JSON 不完整、整批作废。
 *
 * <p>{@code narrate}（AI 现场讲词）与 optimize 并列：单页、单次调用、输出纯文本口播——
 * 无状态语义与 optimize 一致（一次请求 = 一页讲词），失败的恢复（重试/跳过）由前端工作项编排，
 * 后端不做任何会话状态。
 */
@Slf4j
@Service
public class PptSpeechServiceImpl implements PptSpeechService {

    /** 缺省值兜底：配置缺失时也不至于把请求打成一次「整篇输出」 */
    private static final int DEFAULT_BATCH_SIZE = 8;
    private static final int DEFAULT_MAX_PAGES = 200;
    private static final int DEFAULT_MAX_REMARK_CHARS = 1500;
    private static final int DEFAULT_MAX_PAGE_INPUT_CHARS = 1200;
    /** narrate 单页输出的字数上限兜底（与 PptProperties.narrateMaxChars 的默认值一致） */
    private static final int DEFAULT_NARRATE_MAX_CHARS = 500;
    /** 补跑轮数：首轮 + 一轮补跑（Q11：缺失的页自动补跑一次） */
    private static final int MAX_ROUNDS = 2;

    private final ChatClient pptChatClient;
    private final PptProperties properties;

    public PptSpeechServiceImpl(ChatClient pptChatClient, PptProperties properties) {
        this.pptChatClient = pptChatClient;
        this.properties = properties;
    }

    @Override
    public PptSpeechOptimizeRespVO optimize(PptSpeechOptimizeReqVO reqVO) {
        List<PptSpeechPageVO> pages = normalizePages(reqVO);
        Integer targetSeconds = reqVO.getTargetSeconds();
        int perPageChars = SpeechScriptUtils.perPageChars(targetSeconds, pages.size(), properties.getCharsPerSecond());
        List<String> outline = buildOutline(pages);
        int batchSize = positive(properties.getBatchSize(), DEFAULT_BATCH_SIZE);
        int maxPageInputChars = positive(properties.getMaxPageInputChars(), DEFAULT_MAX_PAGE_INPUT_CHARS);

        Map<Integer, String> generated = new LinkedHashMap<>();
        List<PptSpeechPageVO> pending = pages;
        for (int round = 1; round <= MAX_ROUNDS && !pending.isEmpty(); round++) {
            for (List<PptSpeechPageVO> batch : SpeechScriptUtils.splitBatches(pending, batchSize)) {
                generated.putAll(generateBatch(targetSeconds, pages.size(), perPageChars,
                        outline, batch, maxPageInputChars));
            }
            pending = pages.stream().filter(page -> !generated.containsKey(page.getIndex())).toList();
            if (!pending.isEmpty()) {
                log.warn("[optimize][第 {} 轮结束仍有 {} 页未生成，继续补跑 indexes={}]",
                        round, pending.size(), indexesOf(pending));
            }
        }

        // 一页都没成功才整体报错；部分失败走响应体的 failedIndexes（前端标红 + 只重试这几页）
        if (generated.isEmpty()) {
            throw exception(PPT_SPEECH_GENERATE_FAILED);
        }
        return buildResponse(pages, generated);
    }

    @Override
    public PptSpeechNarrateRespVO narrate(PptSpeechNarrateReqVO reqVO) {
        String remark = SpeechScriptUtils.sanitize(reqVO == null ? null : reqVO.getRemark());
        String text = SpeechScriptUtils.sanitize(reqVO == null ? null : reqVO.getText());
        // 备注与正文全空 = 没有可讲的内容：前端工作项对这种页应当走「跳过」，这里是接口侧兜底
        if (remark.isBlank() && text.isBlank()) {
            throw exception(PPT_SPEECH_NARRATE_INPUT_EMPTY);
        }

        String title = reqVO.getTitle();
        PptSpeechNarrateReqVO.Context context = reqVO.getContext();
        int maxPageInputChars = positive(properties.getMaxPageInputChars(), DEFAULT_MAX_PAGE_INPUT_CHARS);
        String userPrompt = SpeechScriptUtils.buildNarratePrompt(title, text, remark,
                context == null ? null : context.getPageCount(),
                context == null ? null : context.getElapsedMin(),
                maxPageInputChars);

        String response;
        try {
            response = pptChatClient.prompt()
                    .system(PptPromptConstants.NARRATE_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[narrate][模型调用失败 index={}] {}", reqVO.getIndex(), e.getMessage());
            throw exception(PPT_SPEECH_NARRATE_FAILED);
        }

        String speech;
        try {
            speech = SpeechScriptUtils.extractPlainText(response);
        } catch (Exception e) {
            log.warn("[narrate][返回解析失败 index={}] {}", reqVO.getIndex(), e.getMessage());
            throw exception(PPT_SPEECH_NARRATE_FAILED);
        }
        int maxNarrateChars = positive(properties.getNarrateMaxChars(), DEFAULT_NARRATE_MAX_CHARS);
        speech = SpeechScriptUtils.clamp(speech, maxNarrateChars);

        PptSpeechNarrateRespVO respVO = new PptSpeechNarrateRespVO();
        respVO.setSpeechText(speech);
        int chars = SpeechScriptUtils.countChars(speech);
        respVO.setChars(chars);
        respVO.setSeconds(SpeechScriptUtils.secondsOf(chars, properties.getCharsPerSecond()));
        log.info("[narrate][现场讲词生成成功 index={} chars={} seconds={}]",
                reqVO.getIndex(), chars, respVO.getSeconds());
        return respVO;
    }

    // ========== 内部方法 ==========

    /**
     * 调一次模型，生成一批页面的讲稿。
     *
     * <p>失败只记日志并返回空 Map：本批的页会落进「未生成」，由补跑轮再试一次。
     * 不在这里抛异常，否则一次网络抖动会让整篇请求前功尽弃。
     *
     * @return 页码 → 讲稿（只包含确实拿到的页）
     */
    private Map<Integer, String> generateBatch(Integer targetSeconds, int totalPages, int perPageChars,
                                               List<String> outline, List<PptSpeechPageVO> batchPages,
                                               int maxPageInputChars) {
        Map<Integer, String> result = new LinkedHashMap<>();
        Set<Integer> allowed = batchPages.stream().map(PptSpeechPageVO::getIndex).collect(Collectors.toSet());
        String userPrompt = SpeechScriptUtils.buildUserPrompt(targetSeconds, totalPages, perPageChars,
                outline, batchPages, maxPageInputChars);

        String response;
        try {
            response = pptChatClient.prompt()
                    .system(PptPromptConstants.SPEECH_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[generateBatch][模型调用失败 indexes={}] {}", allowed, e.getMessage());
            return result;
        }

        try {
            List<PptSpeechResultVO> items = JSONUtil.toList(
                    JSONUtil.parseArray(SpeechScriptUtils.extractJsonArray(response)), PptSpeechResultVO.class);
            int maxRemarkChars = positive(properties.getMaxRemarkChars(), DEFAULT_MAX_REMARK_CHARS);
            for (PptSpeechResultVO item : items) {
                if (item == null || item.getIndex() == null || item.getRemark() == null || item.getRemark().isBlank()) {
                    continue;
                }
                // 只收本批点过名的页：模型偶尔会把别的页一起吐出来，直接丢掉比覆盖回去安全
                if (!allowed.contains(item.getIndex())) {
                    continue;
                }
                result.put(item.getIndex(), SpeechScriptUtils.clamp(item.getRemark().trim(), maxRemarkChars));
            }
        } catch (Exception e) {
            log.warn("[generateBatch][返回解析失败 indexes={}] {}", allowed, e.getMessage());
        }
        return result;
    }

    /** 组装响应：按请求页序输出讲稿与失败页码，字数/秒数由后端算（换算常数只此一处） */
    private PptSpeechOptimizeRespVO buildResponse(List<PptSpeechPageVO> pages, Map<Integer, String> generated) {
        List<PptSpeechResultVO> results = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        for (PptSpeechPageVO page : pages) {
            String remark = generated.get(page.getIndex());
            if (remark == null || remark.isBlank()) {
                failed.add(page.getIndex());
                continue;
            }
            PptSpeechResultVO item = new PptSpeechResultVO();
            item.setIndex(page.getIndex());
            item.setRemark(remark);
            int chars = SpeechScriptUtils.countChars(remark);
            item.setChars(chars);
            item.setSeconds(SpeechScriptUtils.secondsOf(chars, properties.getCharsPerSecond()));
            results.add(item);
        }
        PptSpeechOptimizeRespVO respVO = new PptSpeechOptimizeRespVO();
        respVO.setPages(results);
        respVO.setFailedIndexes(failed);
        log.info("[optimize][讲稿生成结束 成功 {} 页 / 失败 {} 页 failedIndexes={}]", results.size(), failed.size(), failed);
        return respVO;
    }

    /** 校验并取出页清单：空清单与超上限都直接报错（前端应保证不触发，这里是兜底） */
    private List<PptSpeechPageVO> normalizePages(PptSpeechOptimizeReqVO reqVO) {
        List<PptSpeechPageVO> pages = reqVO == null || reqVO.getPages() == null
                ? List.of()
                : reqVO.getPages().stream()
                        .filter(Objects::nonNull)
                        .filter(page -> page.getIndex() != null)
                        .toList();
        if (pages.isEmpty()) {
            throw exception(PPT_SPEECH_PAGES_EMPTY);
        }
        int maxPages = positive(properties.getMaxPages(), DEFAULT_MAX_PAGES);
        if (pages.size() > maxPages) {
            throw exception(PPT_SPEECH_PAGES_TOO_MANY, maxPages);
        }
        return pages;
    }

    /** 全篇大纲：每页一行标题（取正文首行，前端提取），空标题退化成「第 N 页」 */
    private List<String> buildOutline(List<PptSpeechPageVO> pages) {
        List<String> outline = new ArrayList<>();
        for (PptSpeechPageVO page : pages) {
            String title = SpeechScriptUtils.truncate(SpeechScriptUtils.sanitize(page.getTitle()), 40);
            outline.add(title.isBlank() ? "第 " + page.getIndex() + " 页" : title);
        }
        return outline;
    }

    private List<Integer> indexesOf(List<PptSpeechPageVO> pages) {
        return pages.stream().map(PptSpeechPageVO::getIndex).toList();
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

}
