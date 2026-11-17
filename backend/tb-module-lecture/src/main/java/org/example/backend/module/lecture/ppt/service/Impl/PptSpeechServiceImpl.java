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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.Base64;
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

    /** 含图片的批次最多 2 页：图片本身不带页码，批次越大越容易对错页 */
    private static final int VISION_BATCH_SIZE = 2;
    /** 单页最多送给模型的图片数（前端已裁到 3 张，这里是接口侧兜底） */
    private static final int MAX_IMAGES_PER_PAGE = 3;
    /** 单张图片解码后的字节上限（前端目标 ≤300KB，留出余量） */
    private static final int MAX_IMAGE_BYTES = 512 * 1024;
    /** data URL 字符上限，避免把超大 base64 解进内存 */
    private static final int MAX_IMAGE_DATA_URL_LENGTH = 800 * 1024;
    /** 只接受浏览器/模型都能稳定处理的位图格式 */
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    /** 全篇页数上限：只用于给 outline 补齐，防止恶意 totalPages 构造超长列表 */
    private static final int MAX_TOTAL_PAGES = 2000;

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
        // 分片请求必须用全篇页数均摊时长；缺省按本片页数（旧调用方行为不变）
        int totalPages = resolveTotalPages(reqVO, pages);
        int perPageChars = SpeechScriptUtils.perPageChars(targetSeconds, totalPages, properties.getCharsPerSecond());
        List<String> outline = resolveOutline(reqVO, pages, totalPages);
        int batchSize = positive(properties.getBatchSize(), DEFAULT_BATCH_SIZE);
        int maxPageInputChars = positive(properties.getMaxPageInputChars(), DEFAULT_MAX_PAGE_INPUT_CHARS);

        Map<Integer, String> generated = new LinkedHashMap<>();
        List<PptSpeechPageVO> pending = pages;
        for (int round = 1; round <= MAX_ROUNDS && !pending.isEmpty(); round++) {
            for (List<PptSpeechPageVO> batch : splitBatches(pending, batchSize)) {
                generated.putAll(generateBatch(targetSeconds, totalPages, perPageChars,
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
        List<Media> images = collectImages(reqVO == null ? null : reqVO.getImages());
        // 备注、正文与图片全空 = 没有可讲的内容：前端工作项对这种页应当走「跳过」，这里是接口侧兜底
        if (remark.isBlank() && text.isBlank() && images.isEmpty()) {
            throw exception(PPT_SPEECH_NARRATE_INPUT_EMPTY);
        }

        String title = reqVO.getTitle();
        PptSpeechNarrateReqVO.Context context = reqVO.getContext();
        int maxPageInputChars = positive(properties.getMaxPageInputChars(), DEFAULT_MAX_PAGE_INPUT_CHARS);
        String userPrompt = SpeechScriptUtils.buildNarratePrompt(title, text, remark,
                context == null ? null : context.getPageCount(),
                context == null ? null : context.getElapsedMin(),
                maxPageInputChars, images.size());

        log.debug("[narrate][发送消息 index={} images={}]", reqVO.getIndex(), images.size());
        String response;
        try {
            ChatClient.ChatClientRequestSpec promptSpec = pptChatClient.prompt()
                    .system(PptPromptConstants.NARRATE_SYSTEM);
            if (images.isEmpty()) {
                response = promptSpec.user(userPrompt).call().content();
            } else {
                UserMessage userMessage = UserMessage.builder().text(userPrompt).media(images).build();
                response = promptSpec.messages(userMessage).call().content();
            }
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

        // 图片按「页序 → 页内面积序」追加到消息里；prompt 用同一顺序标注每张图属于哪页
        List<Media> images = new ArrayList<>();
        Map<Integer, List<Integer>> imagePositions = new LinkedHashMap<>();
        for (PptSpeechPageVO page : batchPages) {
            List<Integer> positions = new ArrayList<>();
            for (Media image : collectImages(page.getImages())) {
                images.add(image);
                positions.add(images.size());
            }
            if (!positions.isEmpty()) {
                imagePositions.put(page.getIndex(), positions);
            }
        }

        String userPrompt = SpeechScriptUtils.buildUserPrompt(targetSeconds, totalPages, perPageChars,
                outline, batchPages, maxPageInputChars, imagePositions);
        log.debug("[generateBatch][发送批次 pages={} images={} positions={}]",
                batchPages.size(), images.size(), imagePositions);

        String response;
        try {
            ChatClient.ChatClientRequestSpec promptSpec = pptChatClient.prompt()
                    .system(PptPromptConstants.SPEECH_SYSTEM);
            if (images.isEmpty()) {
                response = promptSpec.user(userPrompt).call().content();
            } else {
                UserMessage userMessage = UserMessage.builder().text(userPrompt).media(images).build();
                response = promptSpec.messages(userMessage).call().content();
            }
        } catch (Exception e) {
            log.warn("[generateBatch][模型调用失败 indexes={} images={}] {}", allowed, images.size(), e.getMessage());
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

    /**
     * 按「是否含图片」切批：含图的批次最多 {@link #VISION_BATCH_SIZE} 页，纯文字批次仍用 batch-size。
     *
     * <p>图片在消息里只按顺序排列、不带页码；一个批次里页数越多，模型把图对错页的概率越高。
     * 纯文字页没有这个风险，保持原有 8 页批量以避免把文本生成也拖慢。
     */
    private List<List<PptSpeechPageVO>> splitBatches(List<PptSpeechPageVO> pages, int textBatchSize) {
        List<List<PptSpeechPageVO>> batches = new ArrayList<>();
        List<PptSpeechPageVO> current = new ArrayList<>();
        boolean currentHasImages = false;

        for (PptSpeechPageVO page : pages) {
            boolean pageHasImages = page.getImages() != null && !page.getImages().isEmpty();
            int limit = pageHasImages || currentHasImages ? VISION_BATCH_SIZE : textBatchSize;
            if (!current.isEmpty() && current.size() >= limit) {
                batches.add(current);
                current = new ArrayList<>();
                currentHasImages = false;
                limit = pageHasImages ? VISION_BATCH_SIZE : textBatchSize;
            }
            current.add(page);
            currentHasImages = currentHasImages || pageHasImages;
        }

        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /** 全篇页数：分片请求以前端传入值为准；非法值回退到本片页数，并限制上限 */
    private int resolveTotalPages(PptSpeechOptimizeReqVO reqVO, List<PptSpeechPageVO> pages) {
        Integer requested = reqVO.getTotalPages();
        if (requested == null || requested <= 0) {
            return pages.size();
        }
        return Math.min(Math.max(pages.size(), requested), MAX_TOTAL_PAGES);
    }

    /** 全篇大纲：分片请求以前端传入值为准（按 totalPages 补齐/截断），否则按本批页面生成 */
    private List<String> resolveOutline(PptSpeechOptimizeReqVO reqVO, List<PptSpeechPageVO> pages, int totalPages) {
        List<String> requested = reqVO.getOutline();
        if (requested == null || requested.isEmpty()) {
            return buildOutline(pages);
        }
        List<String> outline = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            String raw = i < requested.size() ? requested.get(i) : null;
            String title = SpeechScriptUtils.truncate(SpeechScriptUtils.sanitize(raw), 40);
            outline.add(title.isBlank() ? "第 " + (i + 1) + " 页" : title);
        }
        return outline;
    }

    /** 收集一页的图片：过滤非法 data URL、超限格式与超过 3 张的余量 */
    private List<Media> collectImages(List<String> dataUrls) {
        if (dataUrls == null || dataUrls.isEmpty()) {
            return List.of();
        }
        List<Media> images = new ArrayList<>();
        for (String dataUrl : dataUrls) {
            if (images.size() >= MAX_IMAGES_PER_PAGE) {
                break;
            }
            Media image = parseImage(dataUrl);
            if (image != null) {
                images.add(image);
            }
        }
        return images;
    }

    /**
     * data URL → Spring AI Media。
     *
     * <p>只接受 image/jpeg、image/png、image/webp；格式/体积不合法的图片静默丢弃
     * （前端已负责压缩与跳过 EMF/WMF，这里是接口侧兜底，不因一张坏图拖垮整批）。
     */
    private Media parseImage(String dataUrl) {
        if (dataUrl == null || dataUrl.length() > MAX_IMAGE_DATA_URL_LENGTH || !dataUrl.startsWith("data:image/")) {
            return null;
        }
        int comma = dataUrl.indexOf(',');
        if (comma <= 0) {
            return null;
        }
        String header = dataUrl.substring(5, comma);
        if (!header.endsWith(";base64")) {
            return null;
        }
        String mimeType = header.substring(0, header.length() - ";base64".length());
        if (!ALLOWED_IMAGE_MIME_TYPES.contains(mimeType)) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                return null;
            }
            return Media.builder().mimeType(MimeTypeUtils.parseMimeType(mimeType)).data(bytes).build();
        } catch (IllegalArgumentException e) {
            log.debug("[parseImage][跳过非法图片 dataUrlLength={}]", dataUrl.length());
            return null;
        }
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
