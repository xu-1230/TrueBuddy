package org.example.backend.module.lecture.ppt.util;

import org.example.backend.module.lecture.ppt.model.vo.PptSpeechPageVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 讲稿生成的纯函数集合（不依赖 Spring，便于用例锁定）。
 *
 * <p>这里放几件容易写错、又完全不依赖外部状态的事：
 * <ol>
 *     <li><b>时长 ↔ 字数换算</b>：语速常数只此一处，避免前后端各存一份导致漂移；</li>
 *     <li><b>prompt 拼装</b>：optimize 的「大纲 + 本批页面」与 narrate 的「单页 + 现场进度」，
 *         格式都被用例锁住；</li>
 *     <li><b>模型返回的提取</b>：optimize 提 JSON 数组、narrate 提纯文本——模型常把返回值
 *         包在 ```json 代码块或解释文字里。</li>
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

    // ========== prompt 拼装（optimize：改写并写回） ==========

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
        return buildUserPrompt(targetSeconds, totalPages, perPageChars, outline, batchPages,
                maxPageInputChars, Map.of());
    }

    /**
     * 拼一个批次的 user prompt（带图片归属说明）。
     *
     * @param imagePositions 页码 → 本页图片在请求图片列表中的序号（1 基），无图片时为空 Map
     */
    public static String buildUserPrompt(Integer targetSeconds, int totalPages, int perPageChars,
                                         List<String> outline, List<PptSpeechPageVO> batchPages,
                                         int maxPageInputChars, Map<Integer, List<Integer>> imagePositions) {
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

        Map<Integer, List<Integer>> positions = imagePositions == null ? Map.of() : imagePositions;

        // 逐页压平后列出：备注里的换行会撑坏「=== 第 N 页 ===」的结构，必须先 sanitize
        for (PptSpeechPageVO page : batchPages) {
            prompt.append("\n=== 第 ").append(page.getIndex()).append(" 页 ===\n");
            prompt.append("标题：").append(blankToNone(sanitize(page.getTitle()))).append('\n');
            prompt.append("正文：").append(blankToNone(truncate(sanitize(page.getText()), maxPageInputChars))).append('\n');
            prompt.append("现有备注：").append(blankToNone(truncate(sanitize(page.getRemark()), maxPageInputChars))).append('\n');
            List<Integer> imageIndexes = positions.get(page.getIndex());
            if (imageIndexes != null && !imageIndexes.isEmpty()) {
                prompt.append("页面图片：本页有 ").append(imageIndexes.size())
                        .append(" 张（对应下方图片列表第 ").append(rangeText(imageIndexes)).append(" 张）\n");
            }
        }

        if (!positions.isEmpty()) {
            prompt.append("\n附带的图片按以下顺序排列（图片本身不带页码）：\n");
            positions.forEach((pageIndex, imageIndexes) -> prompt.append("- 第 ")
                    .append(rangeText(imageIndexes)).append(" 张：第 ").append(pageIndex).append(" 页\n"));
            prompt.append("请把每张图视为对应页面的一部分；图片与正文/备注不一致时，"
                    + "比较两者后自行判断，无法判断时不要陈述有争议的数据。\n");
        }

        prompt.append("\n请只针对以上 ").append(batchPages.size())
                .append(" 页输出 JSON 数组（元素形如 {\"index\": 3, \"remark\": \"…\"}），不要输出任何解释文字。");
        return prompt.toString();
    }

    // ========== prompt 拼装（narrate：AI 现场讲词，念一次就丢） ==========

    /**
     * 拼 narrate 的 user prompt：单页 + 现场进度，纯函数便于用例锁定。
     *
     * <p>与 {@link #buildUserPrompt} 的区别：narrate 只讲一页、不要大纲、不要 JSON；
     * 「现场进度」让模型对节奏有感知（快讲完了就收束，别铺开新话题）。
     *
     * @param title            页标题（可空）
     * @param text             页正文（可空）
     * @param remark           现有备注（可空；讲词蓝本）
     * @param pageCount        全篇总页数（可空）
     * @param elapsedMin       已讲分钟数（可空）
     * @param maxPageInputChars 正文/备注送进 prompt 的字符上限
     */
    public static String buildNarratePrompt(String title, String text, String remark,
                                            Integer pageCount, Integer elapsedMin, int maxPageInputChars) {
        return buildNarratePrompt(title, text, remark, pageCount, elapsedMin, maxPageInputChars, 0);
    }

    /**
     * 拼 narrate 的 user prompt（带图片说明）。
     *
     * @param imageCount 本页附带的图片张数（0 表示无图）
     */
    public static String buildNarratePrompt(String title, String text, String remark,
                                            Integer pageCount, Integer elapsedMin, int maxPageInputChars,
                                            int imageCount) {
        StringBuilder prompt = new StringBuilder();
        if (pageCount != null && pageCount > 0) {
            prompt.append("现场进度：共 ").append(pageCount).append(" 页。");
        }
        if (elapsedMin != null && elapsedMin > 0) {
            prompt.append("已讲约 ").append(elapsedMin).append(" 分钟。");
        }
        if (!prompt.isEmpty()) {
            prompt.append('\n');
        }
        prompt.append("=== 页面信息 ===\n");
        prompt.append("标题：").append(blankToNone(sanitize(title))).append('\n');
        prompt.append("正文：").append(blankToNone(truncate(sanitize(text), maxPageInputChars))).append('\n');
        prompt.append("现有备注：").append(blankToNone(truncate(sanitize(remark), maxPageInputChars))).append('\n');
        if (imageCount > 0) {
            prompt.append("页面图片：本页附带 ").append(imageCount)
                    .append(" 张图片，见请求中的图片列表；请结合图片中可见的内容讲解。\n");
        }
        prompt.append("\n请输出这一页的口播讲稿正文（纯文本，不要任何解释）。");
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

    /**
     * 从模型返回中提取口播正文：剥离 markdown 代码围栏与包裹引号。
     *
     * <p>narrate 的输出契约是「纯文本」，但模型偶尔仍会套上代码块或引号——
     * 念稿前剥掉，否则学生会把「反引号」听进去。
     *
     * @throws IllegalArgumentException 模型返回为空或剥完为空
     */
    public static String extractPlainText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("模型返回为空");
        }
        String cleaned = text.trim();
        // 剥 ``` 围栏（只剥首尾成对的，正文里的反引号不动）
        if (cleaned.startsWith("```")) {
            int firstBreak = cleaned.indexOf('\n');
            cleaned = firstBreak > 0 ? cleaned.substring(firstBreak + 1) : "";
            int lastFence = cleaned.lastIndexOf("```");
            if (lastFence >= 0) {
                cleaned = cleaned.substring(0, lastFence);
            }
            cleaned = cleaned.trim();
        }
        // 剥成对包裹引号（模型爱给口播稿加引号）
        if (cleaned.length() >= 2
                && ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("“") && cleaned.endsWith("”")))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("模型返回里没有讲稿正文");
        }
        return cleaned;
    }

    private static double charsPerSecond(Double configured) {
        return configured == null || configured <= 0 ? DEFAULT_CHARS_PER_SECOND : configured;
    }

    private static String blankToNone(String text) {
        return text == null || text.isBlank() ? "（无）" : text;
    }

    /** 图片序号区间：1 张写「3」，连续多张写「1~2」；列表为空时写「0」 */
    private static String rangeText(List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return "0";
        }
        int first = indexes.get(0);
        int last = indexes.get(indexes.size() - 1);
        return first == last ? String.valueOf(first) : first + "~" + last;
    }

}
