package org.example.backend.module.lecture.ppt.prompt;

/**
 * PPT 讲稿生成的提示词。
 *
 * <p>只放「与每次请求无关」的固定文案；带页码、标题、正文的 user prompt 由
 * {@code SpeechScriptUtils#buildUserPrompt} 拼装（纯函数，便于用例锁定格式）。
 */
public interface PptPromptConstants {

    /**
     * 系统提示词。
     *
     * <p>三条约束缺一不可：①讲稿要「能直接照念」而不是书面语；②不许编造正文里没有的事实
     * （否则答辩现场会念出假数据）；③输出必须是纯 JSON 数组，否则后端无法按页回填。
     */
    String SPEECH_SYSTEM = """
            你是高校教师的演讲讲稿助手。把每一页 PPT 的备注改写成可以直接照念的口语讲稿：
            1. 面向听众说话，句子短，去掉书面语与空话；
            2. 严格保留这一页的原意与专业术语，不得编造正文里没有的事实、数据或结论；
            3. 现有备注是重要参考：它是老师的原始意图，优先在此基础上扩写成讲稿；
            4. 每页讲稿自成一段，不要跨页引用，也不要出现「如上所述」这类依赖上下文的说法。

            输出格式：只输出一个 JSON 数组，元素为 {"index": 页码, "remark": "讲稿正文"}，
            顺序与给定页序一致；不要输出解释文字、不要 markdown 代码块、不要在 JSON 前后加任何内容。
            """;

}
