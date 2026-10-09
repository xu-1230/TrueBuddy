package org.example.backend.module.lecture.livekit;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.example.backend.module.lecture.config.MeetingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LiveKit 访问令牌签发的回归锁定。
 *
 * <p>为什么值得锁：令牌是前后端与 LiveKit 服务端之间唯一的契约 —— claims 写错、签名方式不对
 * （例如带上 base64 padding），表现是「学生端连不上房间」，而排查成本极高。这里断言到字段级，
 * 并用**独立实现的 HMAC** 复算签名（不是自己验自己）。
 *
 * <p>另有一个探针用例（{@code -DlivekitProbe=true}）会把服务端令牌写到
 * {@code target/livekit-probe-token.txt}，用于对着真实 LiveKit 做一次验收：
 * <pre>
 *   curl -X POST http://127.0.0.1:7880/twirp/livekit.RoomService/ListRooms \
 *        -H "Authorization: Bearer &lt;该令牌&gt;" -H "Content-Type: application/json" -d '{}'
 * </pre>
 */
class LiveKitTokenServiceTest {

    private static final String API_KEY = "testkey";
    private static final String API_SECRET = "testsecret-0123456789abcdef";
    private static final String MEETING_NO = "123456";
    private static final String ROOM_NAME = "meeting-123456";

    private LiveKitTokenService newService() {
        MeetingProperties properties = new MeetingProperties();
        properties.setLivekitApiKey(API_KEY);
        properties.setLivekitApiSecret(API_SECRET);
        properties.setLivekitTokenTtlHours(6);
        return new LiveKitTokenService(properties);
    }

    @Test
    @DisplayName("JWT 结构：三段、base64url 无 padding、header 为 HS256")
    void tokenStructure() {
        String token = newService().createHostToken(MEETING_NO);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT 必须是三段");
        for (String part : parts) {
            assertFalse(part.contains("="), "JWT 各段必须是 base64url 且不带 padding");
        }
        JSONObject header = JSONUtil.parseObj(decode(parts[0]));
        assertEquals("HS256", header.getStr("alg"));
        assertEquals("JWT", header.getStr("typ"));
    }

    @Test
    @DisplayName("签名：与独立计算的 HMAC-SHA256 一致")
    void signatureMatchesIndependentHmac() throws Exception {
        String token = newService().createHostToken(MEETING_NO);
        String[] parts = token.split("\\.");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(API_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(expected, Base64.getUrlDecoder().decode(parts[2]), "签名必须可被同一 secret 复算出来");
    }

    @Test
    @DisplayName("老师令牌：可发布、身份 host、显示名为「老师」")
    void hostToken() {
        JSONObject payload = payloadOf(newService().createHostToken(MEETING_NO));
        assertEquals(API_KEY, payload.getStr("iss"));
        assertEquals("host", payload.getStr("sub"));
        assertEquals("老师", payload.getStr("name"));

        JSONObject video = payload.getJSONObject("video");
        assertEquals(ROOM_NAME, video.getStr("room"));
        assertTrue(video.getBool("roomJoin"), "老师要进房间");
        assertTrue(video.getBool("canPublish"), "老师必须能发布（屏幕共享）");
        assertTrue(video.getBool("canSubscribe"));
        assertTrue(video.getBool("canPublishData"), "聊天走 DataChannel，必须能发数据");
        assertFalse(video.containsKey("roomAdmin"), "入会令牌不应带房间管理权限");
    }

    @Test
    @DisplayName("学生令牌：不可发布、只能订阅，但能发 DataChannel 聊天")
    void attendeeToken() {
        JSONObject payload = payloadOf(newService().createAttendeeToken(MEETING_NO, 7L, "张三"));
        assertEquals("attendee-7", payload.getStr("sub"));
        assertEquals("张三", payload.getStr("name"));

        JSONObject video = payload.getJSONObject("video");
        assertEquals(ROOM_NAME, video.getStr("room"));
        assertTrue(video.getBool("roomJoin"));
        assertFalse(video.getBool("canPublish"), "学生不得发布：由服务端强制，不靠前端自觉");
        assertTrue(video.getBool("canSubscribe"));
        assertTrue(video.getBool("canPublishData"), "学生也要能发言");

        JSONObject metadata = JSONUtil.parseObj(payload.getStr("metadata"));
        assertEquals(2, metadata.getInt("role"));
        assertEquals("张三", metadata.getStr("nickname"));
    }

    @Test
    @DisplayName("服务端令牌：不入房间、带房间管理权限（结束会议 = 删房间）")
    void serverToken() {
        JSONObject video = payloadOf(newService().createServerToken(MEETING_NO)).getJSONObject("video");
        assertFalse(video.getBool("roomJoin"), "服务端令牌不需要进房间");
        assertTrue(video.getBool("roomAdmin"), "DeleteRoom 需要 roomAdmin");
        assertEquals(ROOM_NAME, video.getStr("room"));
    }

    @Test
    @DisplayName("数字人令牌：可发布音频轨，但不发 DataChannel（聊天仍是师生特权）")
    void agentToken() {
        JSONObject payload = payloadOf(newService().createAgentToken(MEETING_NO));
        assertEquals(LiveKitTokenService.AGENT_IDENTITY, payload.getStr("sub"),
                "前端按 identity 识别数字人轨，身份串不能改");
        assertEquals("数字人", payload.getStr("name"));

        JSONObject video = payload.getJSONObject("video");
        assertEquals(ROOM_NAME, video.getStr("room"));
        assertTrue(video.getBool("roomJoin"));
        assertTrue(video.getBool("canPublish"), "数字人必须能发布音频轨");
        assertTrue(video.getBool("canSubscribe"));
        assertFalse(video.getBool("canPublishData"), "数字人只出声音，不能替人发消息");
        assertFalse(video.containsKey("roomAdmin"));

        JSONObject metadata = JSONUtil.parseObj(payload.getStr("metadata"));
        assertEquals(3, metadata.getInt("role"), "role=3 是数字人，与消息表 kind=2 的口径配套");
    }

    @Test
    @DisplayName("有效期与时钟偏差：exp ≈ 6 小时后、nbf 略早于当前时刻")
    void expiry() {
        JSONObject payload = payloadOf(newService().createHostToken(MEETING_NO));
        long now = System.currentTimeMillis() / 1000;
        long exp = payload.getLong("exp");
        long nbf = payload.getLong("nbf");
        assertTrue(exp > now + 5 * 3600 && exp < now + 7 * 3600, "exp 应在 6 小时附近，实际相差 " + (exp - now) + "s");
        assertTrue(nbf < now, "nbf 应允许时钟偏差（略早于当前时刻）");
    }

    @Test
    @DisplayName("未配置 Key/Secret：isConfigured=false，签发直接抛错（不产出半成品令牌）")
    void notConfigured() {
        LiveKitTokenService service = new LiveKitTokenService(new MeetingProperties());
        assertFalse(service.isConfigured());
        assertThrows(IllegalStateException.class, () -> service.createHostToken(MEETING_NO));
    }

    @Test
    @DisplayName("信令地址：未配置时按局域网 IP 推导，配置后原样使用并去掉尾斜杠")
    void signallingUrl() {
        MeetingProperties properties = new MeetingProperties();
        properties.setLivekitPort(7880);
        String derived = new LiveKitTokenService(properties).resolveSignallingUrl();
        assertTrue(derived.matches("^ws://[0-9.]+:7880$"), "推导结果应形如 ws://<局域网IP>:7880，实际 " + derived);

        properties.setLivekitUrl("ws://192.168.43.1:7880/");
        assertEquals("ws://192.168.43.1:7880", new LiveKitTokenService(properties).resolveSignallingUrl());
    }

    /**
     * 探针：把服务端令牌写到 {@code target/} 供真实服务端验收（默认不落盘）。
     *
     * <p>凭据默认用测试常量；要拿**部署中的** LiveKit 验收时，用
     * {@code -DlivekitProbeKey=... -DlivekitProbeSecret=...} 注入（与 {@code D:\ctf\livekit\.env} 一致的那一对）。
     */
    @Test
    void probeTokenForRealServerCheck() throws IOException {
        if (!Boolean.getBoolean("livekitProbe")) {
            return;
        }
        MeetingProperties properties = new MeetingProperties();
        properties.setLivekitApiKey(System.getProperty("livekitProbeKey", API_KEY));
        properties.setLivekitApiSecret(System.getProperty("livekitProbeSecret", API_SECRET));
        properties.setLivekitTokenTtlHours(6);

        String token = new LiveKitTokenService(properties).createServerToken(MEETING_NO);
        Path target = Path.of("target", "livekit-probe-token.txt");
        Files.writeString(target, token, StandardCharsets.UTF_8);
        System.out.println("PROBE token written to " + target.toAbsolutePath());
    }

    // ========== 工具方法 ==========

    private JSONObject payloadOf(String token) {
        return JSONUtil.parseObj(decode(token.split("\\.")[1]));
    }

    private String decode(String base64urlSegment) {
        return new String(Base64.getUrlDecoder().decode(base64urlSegment), StandardCharsets.UTF_8);
    }

}
