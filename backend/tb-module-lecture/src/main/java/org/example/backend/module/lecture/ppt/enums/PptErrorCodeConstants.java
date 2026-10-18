package org.example.backend.module.lecture.ppt.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码：lecture 模块的 ppt 子域（PPT 讲稿生成）
 *
 * <p>分段说明：{@code 1_001}~{@code 1_006} 已被 infra / test / knowledgebase / homework / workflow / test2
 * 占用，{@code 1_007_000_xxx} 是同一模块下的模拟会议，本子域顺延取 {@code 1_008_000_xxx}。
 */
public interface PptErrorCodeConstants {

    /** 请求里没有页面（前端应当保证至少一页） */
    ErrorCode PPT_SPEECH_PAGES_EMPTY = new ErrorCode(1_008_000_000, "请提供需要优化的页面");
    /** 单次页数超过安全上限 */
    ErrorCode PPT_SPEECH_PAGES_TOO_MANY = new ErrorCode(1_008_000_001, "单次最多优化 {} 页，请分多次进行");
    /** 所有批次都失败（部分失败不走这里，而是放在响应体的 failedIndexes 里） */
    ErrorCode PPT_SPEECH_GENERATE_FAILED = new ErrorCode(1_008_000_002, "讲稿生成失败，请稍后重试");

}
