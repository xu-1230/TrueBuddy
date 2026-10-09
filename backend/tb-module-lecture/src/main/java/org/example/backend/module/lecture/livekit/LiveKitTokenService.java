package org.example.backend.module.lecture.livekit;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.MeetingProperties;
import org.example.backend.module.lecture.util.LanAddressUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LiveKit 访问令牌签发（HS256，手写 JWT）。
 *
 * <p>为什么不用官方 JVM SDK（{@code io.livekit:livekit-server}）：本项目只需要「签发访问令牌」，
 * 而那个 SDK 是 Kotlin 写的、会带进一整套运行时与 protobuf；令牌本身只是标准 HS256 JWT，
 * 用项目已有的 Hutool 约 30 行即可，claims 结构以 LiveKit CLI 的 {@code lk token create} 为准。
 *
 * <p><b>claims 与授权口径</b>（已用 {@code RoomService/ListRooms} 在真实服务端验证过被接受）：
 * <ul>
 *   <li>{@code iss} = API Key，{@code sub}/{@code jti} = 身份，{@code nbf}/{@code exp} 有效期</li>
 *   <li>{@code name} = 昵称（LiveKit 的显示名），{@code metadata} = {@code {"role":1|2}}，供前端区分师生</li>
 *   <li>{@code video.roomJoin/room/canPublish/canSubscribe/canPublishData}：<b>学生只能订阅</b>，
 *       「谁能发布」由服务端强制，不靠前端自觉</li>
 *   <li>{@code video.roomAdmin/roomList/roomCreate}：服务端管理令牌（本项目用于「结束会议 = 删房间」）</li>
 * </ul>
 *
 * <p>本类同时是 LiveKit 配置的唯一入口（是否有地址、如何推导都从这里出去）。
 */
@Slf4j
@Component
public class LiveKitTokenService {

    /** 主持人身份 */
    public static final String HOST_IDENTITY = "host";
    /** 服务端身份（调用 RoomService 时使用） */
    public static final String SERVER_IDENTITY = "server";
    /** 数字人身份：worker 用它进房间，前端按此 identity 识别哪条音频轨是数字人 */
    public static final String AGENT_IDENTITY = "digital-human";
    /** 主持人角色（与 tb-module-lecture 的 role 口径一致） */
    private static final int ROLE_HOST = 1;
    /** 学生角色 */
    private static final int ROLE_STUDENT = 2;
    /** 数字人角色 */
    private static final int ROLE_AGENT = 3;
    /** 允许的时钟偏差（秒）：避免刚签发就被判「尚未生效」 */
    private static final long CLOCK_SKEW_SECONDS = 10;

    private final MeetingProperties meetingProperties;

    public LiveKitTokenService(MeetingProperties meetingProperties) {
        this.meetingProperties = meetingProperties;
    }

    /**
     * 房间名：一个会议一个房间。
     */
    public static String roomNameOf(String meetingNo) {
        return "meeting-" + meetingNo;
    }

    /**
     * 学生身份：用参会人 id 保证唯一（同一会议内重名已由昵称去重处理）。
     */
    public static String attendeeIdentityOf(Long attendeeId) {
        return "attendee-" + attendeeId;
    }

    /**
     * 是否已配置 LiveKit（缺 Key/Secret 时会议仍可创建、可看历史，但无法入会与发布）。
     */
    public boolean isConfigured() {
        return StrUtil.isNotBlank(meetingProperties.getLivekitApiKey())
                && StrUtil.isNotBlank(meetingProperties.getLivekitApiSecret());
    }

    /**
     * 老师端令牌：可发布（屏幕共享），身份 {@code host}。
     */
    public String createHostToken(String meetingNo) {
        return createToken(roomNameOf(meetingNo), HOST_IDENTITY, "老师", ROLE_HOST, true, false, true);
    }

    /**
     * 学生端令牌：不可发布、只能订阅，但可发 DataChannel（聊天）。
     */
    public String createAttendeeToken(String meetingNo, Long attendeeId, String nickname) {
        return createToken(roomNameOf(meetingNo), attendeeIdentityOf(attendeeId), nickname, ROLE_STUDENT, false, false, true);
    }

    /**
     * 数字人令牌：可发布音频轨，但<b>不发 DataChannel</b>（聊天仍是师生两端的特权，数字人只出声音）。
     */
    public String createAgentToken(String meetingNo) {
        return createToken(roomNameOf(meetingNo), AGENT_IDENTITY, "数字人", ROLE_AGENT, true, false, false);
    }

    /**
     * 服务端令牌：房间管理权限，用于 {@code RoomService/DeleteRoom}（结束会议时把学生踢出房间）。
     */
    public String createServerToken(String meetingNo) {
        return createToken(roomNameOf(meetingNo), SERVER_IDENTITY, "服务端", ROLE_HOST, false, true, true);
    }

    /**
     * 下发给前端的 LiveKit 信令地址：优先取配置，留空则按本机局域网 IPv4 推导。
     */
    public String resolveSignallingUrl() {
        String configured = meetingProperties.getLivekitUrl();
        if (StrUtil.isNotBlank(configured)) {
            String trimmed = configured.trim();
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        return "ws://" + LanAddressUtils.detectLanIpv4() + ":" + meetingProperties.getLivekitPort();
    }

    /**
     * 把信令地址换成 HTTP 基地址（{@code ws→http}、{@code wss→https}），供 Twirp RoomService 调用。
     */
    public String resolveHttpBaseUrl() {
        String signalling = resolveSignallingUrl();
        if (signalling.startsWith("wss://")) {
            return "https://" + signalling.substring("wss://".length());
        }
        if (signalling.startsWith("ws://")) {
            return "http://" + signalling.substring("ws://".length());
        }
        return signalling;
    }

    // ========== 内部方法 ==========

    private String createToken(String roomName, String identity, String displayName, int role,
                              boolean canPublish, boolean roomAdmin, boolean canPublishData) {
        String apiKey = meetingProperties.getLivekitApiKey();
        String apiSecret = meetingProperties.getLivekitApiSecret();
        if (StrUtil.isBlank(apiKey) || StrUtil.isBlank(apiSecret)) {
            // 调用方（MeetingServiceImpl）在对外接口上已做「未配置」校验，这里兜底防误用
            throw new IllegalStateException("LiveKit API Key/Secret 未配置，无法签发访问令牌");
        }

        Map<String, Object> video = new LinkedHashMap<>();
        // 房间管理令牌不需要加入房间
        video.put("roomJoin", !roomAdmin);
        video.put("room", roomName);
        video.put("canPublish", canPublish);
        video.put("canSubscribe", true);
        // 聊天走 DataChannel，因此师生两端必须开数据发布权限；数字人只出声音，故显式关闭
        video.put("canPublishData", canPublishData);
        if (roomAdmin) {
            video.put("roomAdmin", true);
            video.put("roomList", true);
            video.put("roomCreate", true);
        }

        long now = System.currentTimeMillis() / 1000;
        long ttlSeconds = Math.max(1, meetingProperties.getLivekitTokenTtlHours()) * 3600L;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("role", role);
        metadata.put("nickname", displayName);

        String token = JWT.create()
                .setPayload("iss", apiKey)
                .setPayload("sub", identity)
                .setPayload("jti", identity)
                .setPayload("name", displayName)
                .setPayload("metadata", JSONUtil.toJsonStr(metadata))
                .setPayload("nbf", now - CLOCK_SKEW_SECONDS)
                .setPayload("exp", now + ttlSeconds)
                .setPayload("video", video)
                .setKey(apiSecret.getBytes(StandardCharsets.UTF_8))
                .sign();
        log.debug("[createToken][签发成功 room={}, identity={}, canPublish={}, roomAdmin={}]",
                roomName, identity, canPublish, roomAdmin);
        return token;
    }

}
