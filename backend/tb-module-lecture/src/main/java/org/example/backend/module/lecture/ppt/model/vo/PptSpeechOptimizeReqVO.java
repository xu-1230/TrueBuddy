package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 讲稿生成请求：{@code pages} 长度 1 即「优化本页」，长度 N 即「优化全部」。
 *
 * <p>两类页面共用一个接口：单页与整篇的差别只是批次数，后端内部同样按批循环，
 * 前端不需要为「全部」再写一套调用。
 */
@Data
public class PptSpeechOptimizeReqVO {

    /** 本场目标总时长（秒），可空：为空时不约束篇幅，只要求口语化 */
    private Integer targetSeconds;

    /** 需要生成讲稿的页面（顺序即幻灯片顺序） */
    private List<PptSpeechPageVO> pages;

}
