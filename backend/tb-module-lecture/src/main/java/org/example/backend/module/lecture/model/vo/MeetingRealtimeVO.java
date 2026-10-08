package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 实时通道接入信息 VO（下发给前端用于连接 LiveKit 房间）。
 *
 * <p>媒体（屏幕共享）与聊天（DataChannel）都在这条连接上；「谁能发布」由令牌里的
 * {@code canPublish} 在服务端强制，前端拿到的令牌无法自行提权。
 */
@Data
public class MeetingRealtimeVO {

    /** LiveKit 信令地址，如 {@code ws://192.168.43.1:7880} */
    private String livekitUrl;
    /** 访问令牌（该身份专属：老师可发布、学生只能订阅） */
    private String livekitToken;

}
