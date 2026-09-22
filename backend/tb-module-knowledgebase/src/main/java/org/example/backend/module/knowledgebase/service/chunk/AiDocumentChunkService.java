package org.example.backend.module.knowledgebase.service.chunk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 语义分片实现：让对话模型给出「分片 ↔ 文本块」的区间划分，再按区间在本地拼接分片正文。
 *
 * <p>设计要点：
 * <ul>
 *   <li>模型只输出块区间，不输出分片正文，避免模型改写原文导致内容失真；</li>
 *   <li>区间在本地做归一化（排序 / 去重叠 / 越界裁剪 / 补齐遗漏），保证分片完整覆盖全部块、不丢内容；</li>
 *   <li>模型调用失败或输出不可解析时回退为「一块一分片」，保证解析流程不中断。</li>
 * </ul>
 */
@Slf4j
@Service
public class AiDocumentChunkService implements DocumentChunkService {

    /** 单次送入模型的文本块总字符上限，超出则按块边界分批调用 */
    private static final int BATCH_CHAR_LIMIT = 6000;

    /** 日志中打印模型原始输出的长度上限 */
    private static final int LOG_PREVIEW_LIMIT = 200;

    private static final String SYSTEM_PROMPT = """
            你是知识库文档切片助手。输入是一篇文档按阅读顺序拆出的连续文本块，每块带编号。
            请按语义完整性把文本块划分为若干分片，每个分片应是一个语义自洽、可独立检索的主题单元。

            要求：
            1. 分片必须由连续编号的文本块组成，不得打乱顺序、不得跳过或重复；
            2. 分片之间不重叠，合起来覆盖全部编号；
            3. 标题块应与其后正文归入同一分片，不要单独成片；
            4. 只输出 JSON 数组，不要解释、不要 markdown 代码块。

            输出格式：
            [{"start":0,"end":3},{"start":4,"end":7}]
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiDocumentChunkService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public List<String> chunk(List<String> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        if (blocks.size() == 1) {
            return List.of(blocks.get(0));
        }
        List<String> chunks = new ArrayList<>();
        for (List<String> batch : partition(blocks)) {
            chunks.addAll(chunkBatch(batch));
        }
        return chunks;
    }

    /** 按字符总量把块列表切分为若干批次（保留块边界，不拆分单个块） */
    private List<List<String>> partition(List<String> blocks) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int size = 0;
        for (String block : blocks) {
            int length = block.length();
            // 单块即超出上限时独占一批，避免与其它块挤在一起
            if (!current.isEmpty() && size + length > BATCH_CHAR_LIMIT) {
                batches.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(block);
            size += length;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /** 对单个批次执行语义分片；模型不可用时回退为逐块分片 */
    private List<String> chunkBatch(List<String> batch) {
        if (batch.size() == 1) {
            return new ArrayList<>(batch);
        }
        String raw;
        try {
            raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(batch))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI 分片调用失败，回退为逐块分片: blocks={}, error={}", batch.size(), e.getMessage());
            return new ArrayList<>(batch);
        }

        List<int[]> ranges = parseRanges(raw, batch.size());
        if (ranges.isEmpty()) {
            log.warn("AI 分片输出不可解析，回退为逐块分片: blocks={}, raw={}",
                    batch.size(), abbreviate(raw));
            return new ArrayList<>(batch);
        }

        List<String> chunks = new ArrayList<>(ranges.size());
        for (int[] range : ranges) {
            String content = joinRange(batch, range[0], range[1]);
            if (StringUtils.hasText(content)) {
                chunks.add(content);
            }
        }
        if (chunks.isEmpty()) {
            return new ArrayList<>(batch);
        }
        log.info("AI 分片完成: blocks={}, chunks={}", batch.size(), chunks.size());
        return chunks;
    }

    private String buildUserPrompt(List<String> batch) {
        StringBuilder builder = new StringBuilder();
        builder.append("文档共 ").append(batch.size()).append(" 个文本块：\n\n");
        for (int i = 0; i < batch.size(); i++) {
            builder.append('[').append(i).append("]\n").append(batch.get(i)).append("\n\n");
        }
        builder.append("请输出分片区间 JSON 数组。");
        return builder.toString();
    }

    /** 解析模型返回的区间数组；任何异常都返回空列表，交由调用方回退 */
    private List<int[]> parseRanges(String raw, int size) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String json = extractJsonArray(raw);
        if (json == null) {
            return List.of();
        }
        List<ChunkRange> parsed;
        try {
            parsed = objectMapper.readValue(json, new TypeReference<List<ChunkRange>>() {});
        } catch (Exception e) {
            log.warn("AI 分片区间 JSON 解析失败: error={}", e.getMessage());
            return List.of();
        }
        if (parsed == null || parsed.isEmpty()) {
            return List.of();
        }
        return normalize(parsed, size);
    }

    /** 归一化区间：排序 + 去重叠 + 越界裁剪 + 补齐遗漏，保证完整覆盖 [0, size-1] */
    private List<int[]> normalize(List<ChunkRange> parsed, int size) {
        List<int[]> sorted = new ArrayList<>();
        for (ChunkRange range : parsed) {
            if (range == null || range.start() == null || range.end() == null) {
                continue;
            }
            int start = range.start();
            int end = range.end();
            if (start < 0 || start >= size || end < start) {
                continue;
            }
            sorted.add(new int[]{start, Math.min(end, size - 1)});
        }
        sorted.sort(Comparator.comparingInt(range -> range[0]));

        List<int[]> result = new ArrayList<>();
        int cursor = 0;
        for (int[] range : sorted) {
            // 与前一分片重叠的部分裁掉，避免同一块被重复收录
            int start = Math.max(range[0], cursor);
            int end = range[1];
            if (start > end) {
                continue;
            }
            result.add(new int[]{start, end});
            cursor = end + 1;
        }
        // 模型漏掉的尾部块逐个成片，保证不丢内容
        while (cursor < size) {
            result.add(new int[]{cursor, cursor});
            cursor++;
        }
        return result;
    }

    /** 拼接指定块区间的正文（闭区间） */
    private String joinRange(List<String> batch, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i <= end && i < batch.size(); i++) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(batch.get(i));
        }
        return builder.toString().strip();
    }

    /** 从模型输出中截取首个 JSON 数组（容忍前后解释性文字与 markdown 代码块围栏） */
    private String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private String abbreviate(String raw) {
        if (raw == null) {
            return "null";
        }
        return raw.length() <= LOG_PREVIEW_LIMIT ? raw : raw.substring(0, LOG_PREVIEW_LIMIT) + "...";
    }

    /** 模型返回的分片区间（起止块序号，闭区间） */
    private record ChunkRange(Integer start, Integer end) {
    }

}
