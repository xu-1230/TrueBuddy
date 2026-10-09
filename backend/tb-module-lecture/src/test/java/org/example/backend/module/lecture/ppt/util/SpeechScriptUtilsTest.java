package org.example.backend.module.lecture.ppt.util;

import org.example.backend.module.lecture.ppt.model.vo.PptSpeechPageVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 讲稿生成纯函数的回归锁定：换算、拼 prompt、提取 JSON。
 *
 * <p>这三处都不依赖 Spring 与模型，是本功能里唯一能被用例锁住的部分（模型返回本身不可断言）。
 */
class SpeechScriptUtilsTest {

    // ========== 时长 / 字数 ==========

    @Test
    @DisplayName("每页目标字数：总时长 ÷ 页数 × 语速（8 分钟 20 页 ≈ 96 字/页）")
    void perPageChars_normal() {
        // 480 秒 × 4 字/秒 ÷ 20 页 = 96
        assertEquals(96, SpeechScriptUtils.perPageChars(480, 20, 4.0));
    }

    @Test
    @DisplayName("每页目标字数：未指定时长或语速缺失时返回 0（=不约束篇幅）")
    void perPageChars_fallback() {
        assertEquals(0, SpeechScriptUtils.perPageChars(null, 20, 4.0));
        assertEquals(0, SpeechScriptUtils.perPageChars(0, 20, 4.0));
        assertEquals(0, SpeechScriptUtils.perPageChars(480, 0, 4.0));
        // 语速配成 0/负数时退化成默认 4.0，而不是除零或算出 0 字
        assertEquals(96, SpeechScriptUtils.perPageChars(480, 20, 0.0));
    }

    @Test
    @DisplayName("字数统计忽略空白：换行与缩进不算字数")
    void countChars_ignoreWhitespace() {
        assertEquals(5, SpeechScriptUtils.countChars("各位\n\n  同学 好"));
        assertEquals(0, SpeechScriptUtils.countChars("   \n  "));
        assertEquals(0, SpeechScriptUtils.countChars(null));
    }

    @Test
    @DisplayName("字数 → 秒数：236 字 ÷ 4 ≈ 59 秒；0 字直接返回 0")
    void secondsOf_normal() {
        assertEquals(59, SpeechScriptUtils.secondsOf(236, 4.0));
        assertEquals(0, SpeechScriptUtils.secondsOf(0, 4.0));
    }

    // ========== 拼 prompt ==========

    @Test
    @DisplayName("prompt 含时长约束、全篇大纲与本批页面，且备注里的换行已被压平")
    void buildUserPrompt_structure() {
        List<PptSpeechPageVO> batch = List.of(page(2, "项目背景", "本课题来自企业真实场景。\n第二行", "开场先问好"));
        String prompt = SpeechScriptUtils.buildUserPrompt(480, 20, 96, List.of("封面", "项目背景"), batch, 1200);

        assertTrue(prompt.contains("本场目标总时长 480 秒"), prompt);
        assertTrue(prompt.contains("全篇共 20 页"), prompt);
        assertTrue(prompt.contains("每页约 24 秒（约 96 字）"), prompt);
        assertTrue(prompt.contains("全篇大纲：\n1. 封面\n2. 项目背景"), prompt);
        assertTrue(prompt.contains("=== 第 2 页 ==="), prompt);
        assertTrue(prompt.contains("标题：项目背景"), prompt);
        assertTrue(prompt.contains("正文：本课题来自企业真实场景。 第二行"), prompt);
        assertTrue(prompt.contains("现有备注：开场先问好"), prompt);
        // 页脚要点名「只输出这几页」，否则模型会顺手把整篇都写出来，输出被截断
        assertTrue(prompt.contains("请只针对以上 1 页输出 JSON 数组"), prompt);
        assertFalse(prompt.contains("\n第二行"), "备注/正文的换行必须被压平，否则会撑坏分页边界");
    }

    @Test
    @DisplayName("prompt：未指定时长时给兜底篇幅要求；空标题/空正文退化为「（无）」")
    void buildUserPrompt_withoutTargetSeconds() {
        List<PptSpeechPageVO> batch = List.of(page(1, null, null, null));
        String prompt = SpeechScriptUtils.buildUserPrompt(null, 1, 0, List.of("第 1 页"), batch, 1200);

        assertTrue(prompt.contains("未指定目标时长"), prompt);
        assertTrue(prompt.contains("标题：（无）"), prompt);
        assertTrue(prompt.contains("正文：（无）"), prompt);
        assertTrue(prompt.contains("现有备注：（无）"), prompt);
    }

    @Test
    @DisplayName("prompt：超长的单页输入按上限截断（带省略号）")
    void buildUserPrompt_truncateLongInput() {
        String longText = "字".repeat(50);
        List<PptSpeechPageVO> batch = List.of(page(1, "标题", longText, null));
        String prompt = SpeechScriptUtils.buildUserPrompt(null, 1, 0, List.of("标题"), batch, 10);

        assertTrue(prompt.contains("字".repeat(10) + "…"), prompt);
        assertFalse(prompt.contains("字".repeat(11)), "超过上限的部分必须被裁掉");
    }

    // ========== 分批 ==========

    @Test
    @DisplayName("分批：8 页一批，最后一批允许不足；空清单返回空批次")
    void splitBatches_normal() {
        List<Integer> pages = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            pages.add(i);
        }
        List<List<Integer>> batches = SpeechScriptUtils.splitBatches(pages, 8);
        assertEquals(3, batches.size());
        assertEquals(8, batches.get(0).size());
        assertEquals(8, batches.get(1).size());
        assertEquals(4, batches.get(2).size());
        assertEquals(1, batches.get(0).get(0));
        assertEquals(20, batches.get(2).get(3));

        assertTrue(SpeechScriptUtils.splitBatches(List.of(), 8).isEmpty());
        assertTrue(SpeechScriptUtils.splitBatches(null, 8).isEmpty());
    }

    @Test
    @DisplayName("分批：批大小非法时整批一次（宁可让上层按默认值兜，也不要静默丢页）")
    void splitBatches_invalidSize() {
        List<Integer> pages = List.of(1, 2, 3);
        List<List<Integer>> batches = SpeechScriptUtils.splitBatches(pages, 0);
        assertEquals(1, batches.size());
        assertEquals(pages, batches.get(0));
    }

    // ========== 提取 JSON ==========

    @Test
    @DisplayName("提取 JSON：剥离 ```json 代码块与前后解释文字")
    void extractJsonArray_withFence() {
        String response = "好的，以下是结果：\n```json\n[{\"index\":1,\"remark\":\"各位好\"}]\n```\n希望有帮助。";
        assertEquals("[{\"index\":1,\"remark\":\"各位好\"}]", SpeechScriptUtils.extractJsonArray(response));
    }

    @Test
    @DisplayName("提取 JSON：模型把数组包在对象里时也能取出内层数组")
    void extractJsonArray_wrappedInObject() {
        String response = "{\"pages\": [{\"index\":1,\"remark\":\"各位好\"}]}";
        assertEquals("[{\"index\":1,\"remark\":\"各位好\"}]", SpeechScriptUtils.extractJsonArray(response));
    }

    @Test
    @DisplayName("提取 JSON：没有数组或返回为空时抛错（由调用方转成「本批失败、等补跑」）")
    void extractJsonArray_invalid() {
        assertThrows(IllegalArgumentException.class, () -> SpeechScriptUtils.extractJsonArray("抱歉，我无法完成"));
        assertThrows(IllegalArgumentException.class, () -> SpeechScriptUtils.extractJsonArray(""));
        assertThrows(IllegalArgumentException.class, () -> SpeechScriptUtils.extractJsonArray(null));
        // 只有左括号（被输出上限截断的典型形态）同样视为失败
        assertThrows(IllegalArgumentException.class,
                () -> SpeechScriptUtils.extractJsonArray("[{\"index\":1,\"remark\":\"被截断的讲稿"));
    }

    @Test
    @DisplayName("硬截断不给省略号：讲稿上限截断后不能被念出一个「…」")
    void clamp_noEllipsis() {
        assertEquals("一二三", SpeechScriptUtils.clamp("一二三四五", 3));
        assertEquals("一二三", SpeechScriptUtils.clamp("一二三", 3));
        assertEquals("一二三", SpeechScriptUtils.clamp("一二三", 0));
    }

    private PptSpeechPageVO page(int index, String title, String text, String remark) {
        PptSpeechPageVO page = new PptSpeechPageVO();
        page.setIndex(index);
        page.setTitle(title);
        page.setText(text);
        page.setRemark(remark);
        return page;
    }

}
