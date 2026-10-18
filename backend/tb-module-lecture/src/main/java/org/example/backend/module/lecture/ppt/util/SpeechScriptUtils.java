package org.example.backend.module.lecture.ppt.util;

import org.example.backend.module.lecture.ppt.model.vo.PptSpeechPageVO;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 讲稿生成的纯函数集合（不依赖 Spring，便于用例锁定）。
 *
 * <p>这里放三件容易写错、又完全不依赖外部状态的事：
 * <ol>
 *     <li><b>时长 ↔ 字数换算</b>：语速常数只此一处，避免前后端各存一份导致漂移；</li>
 *     <li><b>prompt 拼装</b>：把「大纲 + 本批页面」拼成固定格式，格式被用例锁住；</li>
 *     <li><b>模型返回的 JSON 提取</b>：模型常把 JSON 包在 ```json 代码块或解释文字里。</li>
 * </ol>
 */
public final class SpeechScriptUtils {

    /** 所有空白（含换行）：用于压平 prompt 输入与统计字数 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** 未指定时长时的兜底语速：与 {@code tb.lecture.ppt.chars-per-second} 的默认值一致 */
    private static final double DEFAULT_CHARS_PER_SECOND = 4.0;

    private SpeechScriptUtils() {
    }

    // ========== 时长 / 字数 ==========

    /**
     * 每页目标字数：{@code 目标总时长 ÷ 页数 × 语速}。
     *
     * @return 目标字数；未指定时长或页数非法时返回 0，表示「不约束篇幅」
     */
    public static int perPageChars(Integer targetSeconds, int totalPages, Double charsPerSecond) {
        if (targetSeconds == null || targetSeconds <= 0 || totalPages <= 0) {
            return 0;
        }
        return (int) Math.round(targetSeconds * charsPerSecond(charsPerSecond) / totalPages);
    }

    /** 字数（去掉所有空白后统计：中文按字计，避免换行把字数算大） */
    public static int countChars(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return WHITESPACE.matcher(text).replaceAll("").length();
    }

    /** 字数 → 预计朗读秒数 */
    public static int secondsOf(int chars, Double charsPerSecond) {
        if (chars <= 0) {
            return 0;
        }
        return (int) Math.round(chars / charsPerSecond(charsPerSecond));
    }

    // ========== prompt 拼装 ==========

    /**
     * 拼一个批次的 user prompt。
     *
     * @param targetSeconds    目标总时长（秒），可为空
     * @param totalPages       全篇页数（用于均摊时长）
     * @param perPageChars     每页目标字数（0 表示不约束）
     * @param outline          全篇大纲（每页一行，序号由本方法补）
     * @param batchPages       本批要生成的页面
     * @param maxPageInputChars 单页正文/备注的字符上限
     */
    public static String buildUserPrompt(Integer targetSeconds, int totalPages, int perPageChars,
                                         List<String> outline, List<PptSpeechPageVO> batchPages,
                                         int maxPageInputChars) {
        StringBuilder prompt = new StringBuilder();
        if (targetSeconds != null && targetSeconds > 0 && totalPages > 0) {
            long perPageSeconds = Math.round(targetSeconds / (double) totalPages);
            prompt.append("本场目标总时长 ").append(targetSeconds).append(" 秒；全篇共 ").append(totalPages)
                    .append(" 页，每页约 ").append(perPageSeconds).append(" 秒（约 ").append(perPageChars).append(" 字）。\n");
        } else {
            prompt.append("未指定目标时长：每页写成 3~5 句可直接照念的讲稿，不要长篇大论。\n");
        }

        prompt.append("全篇大纲：\n");
        for (int i = 0; i < outline.size(); i++) {
            prompt.append(i + 1).append(". ").append(blankToNone(outline.get(i))).append('\n');
        }

        // 逐页压平后列出：备注里的换行会撑坏「=== 第 N 页 ===」的结构，必须先 sanitize
        for (PptSpeechPageVO page : batchPages) {
            prompt.append("\n=== 第 ").append(page.getIndex()).append(" 页 ===\n");
            prompt.append("标题：").append(blankToNone(sanitize(page.getTitle()))).append('\n');
            prompt.append("正文：").append(blankToNone(truncate(sanitize(page.getText()), maxPageInputChars))).append('\n');
            prompt.append("现有备注：").append(blankToNone(truncate(sanitize(page.getRemark()), maxPageInputChars))).append('\n');
        }

        prompt.append("\n请只针对以上 ").append(batchPages.size())
                .append(" 页输出 JSON 数组（元素形如 {\"index\": 3, \"remark\": \"…\"}），不要输出任何解释文字。");
        return prompt.toString();
    }

    // ========== 通用小工具 ==========

    /** 按 size 切批（size 非法时整批一次返回，不抛异常：调用方已在配置侧兜过默认值） */
    public static <T> List<List<T>> splitBatches(List<T> source, int size) {
        List<List<T>> batches = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return batches;
        }
        int step = size <= 0 ? source.size() : size;
        for (int start = 0; start < source.size(); start += step) {
            batches.add(new ArrayList<>(source.subList(start, Math.min(start + step, source.size()))));
        }
        return batches;
    }

    /** 压平成单行：换行、制表符、连续空格统一成一个空格 */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    /** 超长截断（带省略号，让模型知道内容被裁过）：用于**送进 prompt 的输入** */
    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (max <= 0 || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    /** 硬截断（不加省略号）：用于**模型输出的讲稿上限**，省略号会被老师原样念出来 */
    public static String clamp(String text, int max) {
        if (text == null) {
            return "";
        }
        return max <= 0 || text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 从模型返回文本中提取 JSON 数组：剥离 ```json 代码块与前后解释性文字。
     *
     * <p>取「第一个 {@code [} 到最后一个 {@code ]}」——与 {@code tb-module-doctemplate}
     * 的 {@code AiContentServiceImpl#extractJson} 同一手法（该项目里已验证可用）。
     *
     * @throws IllegalArgumentException 找不到 JSON 数组（由调用方转成「本批失败、等补跑」）
     */
    public static String extractJsonArray(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("模型返回为空");
        }
        String trimmed = text.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型返回里没有 JSON 数组");
        }
        return trimmed.substring(start, end + 1);
    }

    private static double charsPerSecond(Double configured) {
        return configured == null || configured <= 0 ? DEFAULT_CHARS_PER_SECOND : configured;
    }

    private static String blankToNone(String text) {
        return text == null || text.isBlank() ? "（无）" : text;
    }

}
