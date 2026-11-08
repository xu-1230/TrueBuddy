package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * AI 现场讲词（narrate）响应体。
 *
 * <p>{@code speechText} 是一次性口播文本：前端拿到后按句切段、经既有 speak SSE 链路播出；
 * 字数与秒数由后端按语速常数算好，控制台可以显示「约 38 秒」这类预估。
 */
@Data
public class PptSpeechNarrateRespVO {

    /** 口播讲稿正文（已按 narrateMaxChars 截断） */
    private String speechText;

    /** 字数（去掉所有空白后统计） */
    private Integer chars;

    /** 预计朗读秒数（字数 ÷ 语速常数） */
    private Integer seconds;

}
