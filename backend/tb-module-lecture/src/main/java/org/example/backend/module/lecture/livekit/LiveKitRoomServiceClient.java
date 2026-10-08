package org.example.backend.module.lecture.livekit;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.MeetingProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LiveKit 房间管理（Twirp HTTP 调 {@code RoomService}）。
 *
 * <p>本项目只用到一个能力：<b>结束会议时删房间</b>。删掉房间会让房间内所有客户端立刻收到
 * {@code Disconnected}，这正好补上「自建 WebSocket 广播链路被删除后，会议结束如何通知学生」的真空。
 *
 * <p>调用口径已在真实服务端验证：{@code POST {http}/twirp/livekit.RoomService/DeleteRoom}，
 * 头 {@code Authorization: Bearer <服务端令牌>}，体 {@code {"room":"meeting-123456"}} → 200 + {@code {}}。
 * 必需的授权是 {@code video.roomAdmin}（项目级 {@code roomList}/{@code roomCreate} 一并签发，便于诊断）。
 */
@Slf4j
@Component
public class LiveKitRoomServiceClient {

    /** Twirp 路径前缀 */
    private static final String TWIRP_BASE = "/twirp/livekit.RoomService";
    /** 单次调用超时（毫秒） */
    private static final int REQUEST_TIMEOUT_MILLIS = 3000;
    /** 房间不存在时 LiveKit 返回 404（实测：删不存在的房间 = 404 not_found，而不是 401） */
    private static final int HTTP_NOT_FOUND = 404;

    private final MeetingProperties meetingProperties;
    private final LiveKitTokenService liveKitTokenService;

    public LiveKitRoomServiceClient(MeetingProperties meetingProperties, LiveKitTokenService liveKitTokenService) {
        this.meetingProperties = meetingProperties;
        this.liveKitTokenService = liveKitTokenService;
    }

    /**
     * 删除房间（幂等、**best-effort**）。
     *
     * <p>失败只记日志：会议状态已经落库为「已结束」，学生端还有 {@code GET /meeting/state} 轮询兜底，
     * 不该因为 LiveKit 不可达而让「结束会议」这个用户动作失败。
     *
     * @param meetingNo 会议号
     * @return 是否删除成功
     */
    public boolean deleteRoom(String meetingNo) {
        if (!liveKitTokenService.isConfigured()) {
            log.warn("[deleteRoom][跳过：LiveKit 未配置 meetingNo={}]", meetingNo);
            return false;
        }
        String roomName = LiveKitTokenService.roomNameOf(meetingNo);
        String url = liveKitTokenService.resolveHttpBaseUrl() + TWIRP_BASE + "/DeleteRoom";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("room", roomName);
        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + liveKitTokenService.createServerToken(meetingNo))
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(REQUEST_TIMEOUT_MILLIS)
                    .execute();
            if (response.isOk()) {
                log.info("[deleteRoom][已删房间 room={}, meetingNo={}]", roomName, meetingNo);
                return true;
            }
            // 房间本就不存在（已被 empty_timeout 回收，或重复结束会议）：目标已达成，按成功处理，
            // 否则「再点一次结束会议」会留下一条误导性的失败日志
            if (response.getStatus() == HTTP_NOT_FOUND) {
                log.info("[deleteRoom][房间本就不存在，按成功处理 room={}, meetingNo={}]", roomName, meetingNo);
                return true;
            }
            log.warn("[deleteRoom][删除失败 room={}, status={}, body={}]",
                    roomName, response.getStatus(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("[deleteRoom][调用 LiveKit 失败 room={}, baseUrl={}]",
                    roomName, meetingProperties.getLivekitUrl(), e);
            return false;
        }
    }

}
