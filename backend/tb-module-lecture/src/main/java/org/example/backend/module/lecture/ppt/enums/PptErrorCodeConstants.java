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
    /** narrate：页面信息全空（备注与正文都没有，没有可讲的内容） */
    ErrorCode PPT_SPEECH_NARRATE_INPUT_EMPTY = new ErrorCode(1_008_000_003, "这一页没有可讲的内容：备注与页面正文均为空");
    /** narrate：模型调用失败或返回为空（前端走「重试/跳过」的 Attention 流程） */
    ErrorCode PPT_SPEECH_NARRATE_FAILED = new ErrorCode(1_008_000_004, "现场讲词生成失败，请重试或跳过本页");

    // ========== 数字人与音色设置 1_008_000_005 起 ==========
    /** 偏好接口没有登录用户（防御性兜底；正常情况下安全框架会先返回 401） */
    ErrorCode PPT_PREFERENCE_LOGIN_REQUIRED = new ErrorCode(1_008_000_005, "请先登录后再保存数字人设置");
    /** 保存偏好时形象编码不在启用目录里 */
    ErrorCode PPT_AVATAR_MODEL_NOT_FOUND = new ErrorCode(1_008_000_006, "所选数字人不存在或已停用");
    /** 保存偏好/播报时音色不存在或不可见 */
    ErrorCode PPT_VOICE_NOT_FOUND = new ErrorCode(1_008_000_007, "所选音色不存在或已删除");
    /** 复刻表单缺名称或性别 */
    ErrorCode PPT_VOICE_CLONE_INPUT_INVALID = new ErrorCode(1_008_000_008, "请填写音色名称并选择性别");
    /** 复刻名称与可见音色重名 */
    ErrorCode PPT_VOICE_NAME_DUPLICATED = new ErrorCode(1_008_000_009, "已有同名音色：{}");
    /** 复刻来源既不是上传也不是链接 */
    ErrorCode PPT_VOICE_SOURCE_INVALID = new ErrorCode(1_008_000_010, "请选择上传音频或填写音频链接");
    /** 粘贴的音频链接不是 http/https */
    ErrorCode PPT_VOICE_URL_INVALID = new ErrorCode(1_008_000_011, "音频链接必须是 http/https 地址");
    /** 上传样本格式或大小不符合要求 */
    ErrorCode PPT_VOICE_SAMPLE_INVALID = new ErrorCode(1_008_000_012, "音频样本仅支持 wav/mp3，且不超过 10MB");
    /** 没有配置公网存储地址时不允许上传样本 */
    ErrorCode PPT_VOICE_UPLOAD_DISABLED = new ErrorCode(1_008_000_013, "当前未配置公网存储，请改用音频链接");
    /** DashScope 声音复刻失败 */
    ErrorCode PPT_VOICE_CLONE_FAILED = new ErrorCode(1_008_000_014, "声音复刻失败：{}");
    /** 只能删除自己复刻的音色 */
    ErrorCode PPT_VOICE_DELETE_FORBIDDEN = new ErrorCode(1_008_000_015, "只能删除自己复刻的音色");
    /** 音色列表只对登录用户开放（防御性兜底） */
    ErrorCode PPT_VOICE_LOGIN_REQUIRED = new ErrorCode(1_008_000_016, "请先登录后再使用音色库");
    /** 复刻元数据超出长度限制 */
    ErrorCode PPT_VOICE_METADATA_INVALID = new ErrorCode(1_008_000_017, "音色名称、特征或语言超出长度限制");

}
