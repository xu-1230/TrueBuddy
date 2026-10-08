package org.example.backend.module.lecture.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 lecture（讲课模块，本期为模拟会议）
 *
 * <p>分段说明：{@code 1_001}~{@code 1_006} 已被 infra / test / knowledgebase /
 * homework / workflow / test2 占用，本模块取 {@code 1_007_000_xxx}。
 */
public interface ErrorCodeConstants {

    // ========== 模拟会议 1_007_000_000 ==========
    ErrorCode MEETING_TITLE_REQUIRED = new ErrorCode(1_007_000_000, "请填写会议标题");
    ErrorCode MEETING_NOT_EXISTS = new ErrorCode(1_007_000_001, "会议不存在");
    ErrorCode MEETING_ALREADY_CLOSED = new ErrorCode(1_007_000_002, "会议已结束");
    ErrorCode MEETING_NICKNAME_REQUIRED = new ErrorCode(1_007_000_003, "请输入你的名字");
    ErrorCode MEETING_NICKNAME_TOO_LONG = new ErrorCode(1_007_000_004, "名字长度不能超过 {} 个字符");
    ErrorCode MEETING_ATTENDEE_LIMIT_EXCEED = new ErrorCode(1_007_000_005, "会议人数已达上限（{} 人）");
    ErrorCode MEETING_HOST_TOKEN_INVALID = new ErrorCode(1_007_000_006, "主持人凭据无效");
    ErrorCode MEETING_NO_GENERATE_FAILED = new ErrorCode(1_007_000_007, "会议号生成失败，请重试");
    ErrorCode MEETING_TITLE_TOO_LONG = new ErrorCode(1_007_000_008, "会议标题长度不能超过 {} 个字符");
    ErrorCode MEETING_ATTENDEE_TOKEN_INVALID = new ErrorCode(1_007_000_009, "参会凭据无效，请重新入会");
    ErrorCode MEETING_LIVEKIT_NOT_CONFIGURED = new ErrorCode(1_007_000_010,
            "实时通道未配置：请先启动 LiveKit（D:\\ctf\\livekit\\start.ps1）并配置 LIVEKIT_API_KEY / LIVEKIT_API_SECRET");
    ErrorCode MEETING_MESSAGE_CONTENT_EMPTY = new ErrorCode(1_007_000_011, "消息内容不能为空");
    ErrorCode MEETING_MESSAGE_CONTENT_TOO_LONG = new ErrorCode(1_007_000_012, "消息长度不能超过 {} 个字符");
    ErrorCode MEETING_SENDER_INVALID = new ErrorCode(1_007_000_013, "发送者凭据无效，请重新入会");

}
