package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * AI 现场讲词（narrate）请求体。
 *
 * <p>与 {@link PptSpeechPageVO} 同一提取口径（前端 {@code buildSpeechPages} 单页版）：
 * 备注（remark）是讲词蓝本，标题/正文（title/text）是页面事实的补充上下文。
 * 与 optimize 的区别：optimize 是「改写并写回」，narrate 是「念一次就丢」的一次性派生物——
 * 口播文本只进会议台账（kind=2 行），绝不写回 remark。
 */
@Data
public class PptSpeechNarrateReqVO {

    /** 页码，从 1 开始（只用于提示词里的现场进度，后端不做对位） */
    private Integer index;

    /** 该页标题（前端取正文首行，可为空） */
    private String title;

    /** 该页正文纯文本（可空） */
    private String text;

    /** 该页现有备注（可空）——讲词蓝本；与 text 至少一个非空 */
    private String remark;

    /** 现场上下文（前端组装，全部可空）：让讲词有「正在讲课」的现场感 */
    private Context context;

    /**
     * 现场上下文。
     *
     * <p>不放 meetingTitle：放映页（编排层所在）并不持有会议标题，硬凑只会得到假数据。
     */
    @Data
    public static class Context {

        /** 全篇总页数（提示词里说「这是第 X 页，共 Y 页」） */
        private Integer pageCount;

        /** 已讲分钟数（工作项开始至今），让模型对节奏有感知 */
        private Integer elapsedMin;
    }

}
