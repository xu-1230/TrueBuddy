package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 讲稿生成响应。
 *
 * <p>部分失败**不走错误码**：成功的页照常返回，失败的页码放进 {@link #failedIndexes}，
 * 前端在对比弹窗里把这几页标红并提供「只重试这些页」。
 * 只有一页都没成功时才整体报错（见 {@code 1_008_000_002}）。
 */
@Data
public class PptSpeechOptimizeRespVO {

    /** 生成成功的页（按请求顺序） */
    private List<PptSpeechResultVO> pages;

    /** 两轮（正常 + 补跑一次）后仍未成功的页码；为空表示全部成功 */
    private List<Integer> failedIndexes;

}
