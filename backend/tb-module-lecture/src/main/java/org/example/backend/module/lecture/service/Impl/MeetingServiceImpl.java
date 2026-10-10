package org.example.backend.module.lecture.service.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.MeetingProperties;
import org.example.backend.module.lecture.livekit.LiveKitRoomServiceClient;
import org.example.backend.module.lecture.livekit.LiveKitTokenService;
import org.example.backend.module.lecture.mapper.LectureMeetingAttendeeMapper;
import org.example.backend.module.lecture.mapper.LectureMeetingMapper;
import org.example.backend.module.lecture.mapper.LectureMeetingMessageMapper;
import org.example.backend.module.lecture.model.LectureMeetingAttendeeDO;
import org.example.backend.module.lecture.model.LectureMeetingDO;
import org.example.backend.module.lecture.model.LectureMeetingMessageDO;
import org.example.backend.module.lecture.model.vo.MeetingAttendeeVO;
import org.example.backend.module.lecture.model.vo.MeetingCloseReqVO;
import org.example.backend.module.lecture.model.vo.MeetingCreateReqVO;
import org.example.backend.module.lecture.model.vo.MeetingCreateRespVO;
import org.example.backend.module.lecture.model.vo.MeetingDetailRespVO;
import org.example.backend.module.lecture.model.vo.MeetingInfoVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinReqVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinRespVO;
import org.example.backend.module.lecture.model.vo.MeetingLeaveReqVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageReqVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageVO;
import org.example.backend.module.lecture.model.vo.MeetingRealtimeVO;
import org.example.backend.module.lecture.service.MeetingService;
import org.example.backend.module.lecture.util.LanAddressUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_ALREADY_CLOSED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_ATTENDEE_TOKEN_INVALID;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_HOST_TOKEN_INVALID;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_LIVEKIT_NOT_CONFIGURED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_MESSAGE_CONTENT_EMPTY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_MESSAGE_CONTENT_TOO_LONG;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_NICKNAME_REQUIRED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_NICKNAME_TOO_LONG;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_NOT_EXISTS;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_NO_GENERATE_FAILED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_SENDER_INVALID;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_TITLE_REQUIRED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_TITLE_TOO_LONG;

/**
 * 模拟会议 Service 实现。
 *
 * <p>职责：会议 / 参会人 / 消息的落库与查询，以及向端上下发 LiveKit 实时接入信息。
 * <b>媒体（屏幕共享）与聊天实时广播都在 LiveKit 上</b>（屏幕共享走音视频轨、聊天走 DataChannel），
 * 因此本实现不含任何内存连接态；后端只在两处参与实时：
 * ① 入会 / 详情 / 状态接口签发访问令牌；② 结束会议时删 LiveKit 房间。
 */
@Slf4j
@Service
public class MeetingServiceImpl implements MeetingService {

    /** 会议状态：进行中 */
    private static final int STATUS_RUNNING = 1;
    /** 会议状态：已结束 */
    private static final int STATUS_CLOSED = 2;
    /** 角色：老师 */
    private static final int ROLE_HOST = 1;
    /** 角色：学生 */
    private static final int ROLE_STUDENT = 2;
    /** 老师发言时的昵称（与 LiveKit 令牌里的 name 保持一致） */
    private static final String HOST_NICKNAME = "老师";
    /** 消息类型：文字 */
    private static final int KIND_TEXT = 1;
    /** 会议号位数 */
    private static final int MEETING_NO_BOUND = 900_000;
    private static final int MEETING_NO_BASE = 100_000;
    /** 会议号生成重试次数 */
    private static final int MEETING_NO_MAX_RETRY = 10;
    /** 标题长度上限 */
    private static final int TITLE_MAX_LENGTH = 100;

    @Resource
    private LectureMeetingMapper meetingMapper;
    @Resource
    private LectureMeetingAttendeeMapper attendeeMapper;
    @Resource
    private LectureMeetingMessageMapper messageMapper;
    @Resource
    private MeetingProperties meetingProperties;
    @Resource
    private LiveKitTokenService liveKitTokenService;
    @Resource
    private LiveKitRoomServiceClient liveKitRoomServiceClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeetingCreateRespVO create(MeetingCreateReqVO reqVO) {
        String title = reqVO.getTitle() == null ? "" : reqVO.getTitle().trim();
        if (title.isEmpty()) {
            throw exception(MEETING_TITLE_REQUIRED);
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw exception(MEETING_TITLE_TOO_LONG, TITLE_MAX_LENGTH);
        }

        LectureMeetingDO meeting = new LectureMeetingDO();
        meeting.setMeetingNo(generateMeetingNo());
        meeting.setTitle(title);
        meeting.setHostToken(randomToken());
        meeting.setStatus(STATUS_RUNNING);
        meetingMapper.insert(meeting);
        log.info("[create][创建会议成功 meetingNo={}, meetingId={}]", meeting.getMeetingNo(), meeting.getId());

        MeetingCreateRespVO respVO = new MeetingCreateRespVO();
        respVO.setMeetingId(meeting.getId());
        respVO.setMeetingNo(meeting.getMeetingNo());
        respVO.setHostToken(meeting.getHostToken());
        respVO.setJoinUrl(resolvePublicBaseUrl() + "/meeting/" + meeting.getMeetingNo());
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeetingJoinRespVO join(MeetingJoinReqVO reqVO) {
        LectureMeetingDO meeting = getMeetingOrThrow(reqVO.getMeetingNo());
        if (STATUS_CLOSED == meeting.getStatus()) {
            throw exception(MEETING_ALREADY_CLOSED);
        }
        // 实时通道未配置时不允许入会：没有画面与聊天的「会议室」没有意义，不如直接给出明确原因
        if (!liveKitTokenService.isConfigured()) {
            throw exception(MEETING_LIVEKIT_NOT_CONFIGURED);
        }

        String nickname = reqVO.getNickname() == null ? "" : reqVO.getNickname().trim();
        if (nickname.isEmpty()) {
            throw exception(MEETING_NICKNAME_REQUIRED);
        }
        int nicknameMaxLength = meetingProperties.getNicknameMaxLength();
        if (nickname.length() > nicknameMaxLength) {
            throw exception(MEETING_NICKNAME_TOO_LONG, nicknameMaxLength);
        }

        LectureMeetingAttendeeDO attendee = new LectureMeetingAttendeeDO();
        attendee.setMeetingId(meeting.getId());
        attendee.setNickname(deduplicateNickname(meeting.getId(), nickname));
        attendee.setAttendeeToken(randomToken());
        attendee.setRole(ROLE_STUDENT);
        attendeeMapper.insert(attendee);
        log.info("[join][入会成功 meetingNo={}, attendeeId={}, nickname={}]",
                meeting.getMeetingNo(), attendee.getId(), attendee.getNickname());

        MeetingJoinRespVO respVO = new MeetingJoinRespVO();
        respVO.setAttendeeId(attendee.getId());
        respVO.setAttendeeToken(attendee.getAttendeeToken());
        respVO.setMeeting(buildMeetingInfo(meeting));
        respVO.setAttendees(buildAttendeeList(meeting.getId()));
        respVO.setMessages(buildMessageList(meeting.getId()));
        respVO.setRealtime(buildAttendeeRealtime(meeting, attendee));
        return respVO;
    }

    @Override
    public MeetingDetailRespVO detail(String meetingNo, String hostToken) {
        LectureMeetingDO meeting = getMeetingOrThrow(meetingNo);
        validateHostToken(meeting, hostToken);

        MeetingDetailRespVO respVO = new MeetingDetailRespVO();
        respVO.setMeeting(buildMeetingInfo(meeting));
        respVO.setAttendees(buildAttendeeList(meeting.getId()));
        respVO.setMessages(buildMessageList(meeting.getId()));
        respVO.setRealtime(buildHostRealtime(meeting));
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leave(MeetingLeaveReqVO reqVO) {
        LectureMeetingDO meeting = getMeetingOrThrow(reqVO.getMeetingNo());
        if (STATUS_CLOSED == meeting.getStatus()) {
            throw exception(MEETING_ALREADY_CLOSED);
        }
        LectureMeetingAttendeeDO attendee = getAttendeeByToken(reqVO.getAttendeeToken());
        if (attendee == null || !meeting.getId().equals(attendee.getMeetingId())) {
            throw exception(MEETING_ATTENDEE_TOKEN_INVALID);
        }
        // 只更新 leave_time IS NULL 的行：sendBeacon 重试时第二次拿不到影响行数，也不会覆盖首次时间
        int updated = attendeeMapper.markLeft(attendee.getId());
        log.info("[leave][学生离会 meetingNo={}, attendeeId={}, updated={}]",
                meeting.getMeetingNo(), attendee.getId(), updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(MeetingCloseReqVO reqVO) {
        LectureMeetingDO meeting = getMeetingOrThrow(reqVO.getMeetingNo());
        validateHostToken(meeting, reqVO.getHostToken());
        if (STATUS_CLOSED == meeting.getStatus()) {
            throw exception(MEETING_ALREADY_CLOSED);
        }
        meetingMapper.updateStatus(meeting.getId(), STATUS_CLOSED, LocalDateTime.now());
        attendeeMapper.markAllLeft(meeting.getId());
        // 删掉 LiveKit 房间：房间内所有客户端会立刻收到 Disconnected，这正是「会议已结束」的实时通知
        // （best-effort，失败只记日志 —— 学生端还有 GET /meeting/state 轮询兜底）
        liveKitRoomServiceClient.deleteRoom(meeting.getMeetingNo());
        log.info("[close][会议已结束 meetingNo={}, meetingId={}]", meeting.getMeetingNo(), meeting.getId());
    }

    // ========== 实时通道（LiveKit）与内部查询 ==========

    @Override
    public LectureMeetingAttendeeDO getAttendeeByToken(String attendeeToken) {
        if (attendeeToken == null || attendeeToken.isBlank()) {
            return null;
        }
        return attendeeMapper.selectByAttendeeToken(attendeeToken.trim());
    }

    @Override
    public MeetingDetailRespVO state(String meetingNo, String attendeeToken) {
        LectureMeetingDO meeting = getMeetingOrThrow(meetingNo);
        LectureMeetingAttendeeDO attendee = getAttendeeByToken(attendeeToken);
        if (attendee == null || !meeting.getId().equals(attendee.getMeetingId())) {
            throw exception(MEETING_ATTENDEE_TOKEN_INVALID);
        }
        MeetingDetailRespVO respVO = new MeetingDetailRespVO();
        respVO.setMeeting(buildMeetingInfo(meeting));
        respVO.setAttendees(buildAttendeeList(meeting.getId()));
        respVO.setMessages(buildMessageList(meeting.getId()));
        // 刷新 / 断线重连后重新取令牌：否则页面刷新一次就再也连不上房间
        respVO.setRealtime(buildAttendeeRealtime(meeting, attendee));
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeetingMessageVO postMessage(MeetingMessageReqVO reqVO) {
        LectureMeetingDO meeting = getMeetingOrThrow(reqVO.getMeetingNo());
        if (STATUS_CLOSED == meeting.getStatus()) {
            throw exception(MEETING_ALREADY_CLOSED);
        }
        String content = reqVO.getContent() == null ? "" : reqVO.getContent().trim();
        if (content.isEmpty()) {
            throw exception(MEETING_MESSAGE_CONTENT_EMPTY);
        }
        int maxLength = meetingProperties.getMessageMaxLength();
        if (content.length() > maxLength) {
            throw exception(MEETING_MESSAGE_CONTENT_TOO_LONG, maxLength);
        }

        Long attendeeId = null;
        String nickname;
        int role;
        if (reqVO.getHostToken() != null && reqVO.getHostToken().equals(meeting.getHostToken())) {
            nickname = HOST_NICKNAME;
            role = ROLE_HOST;
        } else {
            LectureMeetingAttendeeDO attendee = getAttendeeByToken(reqVO.getAttendeeToken());
            if (attendee == null || !meeting.getId().equals(attendee.getMeetingId())) {
                throw exception(MEETING_SENDER_INVALID);
            }
            attendeeId = attendee.getId();
            nickname = attendee.getNickname();
            role = attendee.getRole() == null ? ROLE_STUDENT : attendee.getRole();
        }
        return sendMessage(meeting.getId(), attendeeId, nickname, role, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeetingMessageVO sendMessage(Long meetingId, Long attendeeId, String nickname, Integer role, String content) {
        return sendMessage(meetingId, attendeeId, nickname, role, content, KIND_TEXT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeetingMessageVO sendMessage(Long meetingId, Long attendeeId, String nickname, Integer role,
                                        String content, Integer kind) {
        LectureMeetingMessageDO message = new LectureMeetingMessageDO();
        message.setMeetingId(meetingId);
        message.setAttendeeId(attendeeId);
        message.setNickname(nickname);
        message.setRole(role);
        message.setContent(content);
        message.setKind(kind);
        messageMapper.insert(message);
        return buildMessageVO(messageMapper.selectById(message.getId()));
    }

    @Override
    public LectureMeetingDO requireRunningMeetingByHost(String meetingNo, String hostToken) {
        LectureMeetingDO meeting = getMeetingOrThrow(meetingNo);
        if (STATUS_CLOSED == meeting.getStatus()) {
            throw exception(MEETING_ALREADY_CLOSED);
        }
        validateHostToken(meeting, hostToken);
        return meeting;
    }

    // ========== 内部方法 ==========

    private LectureMeetingDO getMeetingOrThrow(String meetingNo) {
        if (meetingNo == null || meetingNo.isBlank()) {
            throw exception(MEETING_NOT_EXISTS);
        }
        LectureMeetingDO meeting = meetingMapper.selectByMeetingNo(meetingNo.trim());
        if (meeting == null) {
            throw exception(MEETING_NOT_EXISTS);
        }
        return meeting;
    }

    private void validateHostToken(LectureMeetingDO meeting, String hostToken) {
        if (hostToken == null || !hostToken.equals(meeting.getHostToken())) {
            throw exception(MEETING_HOST_TOKEN_INVALID);
        }
    }

    /** 生成 6 位会议号，与未删除会议去重（重试上限后失败） */
    private String generateMeetingNo() {
        for (int i = 0; i < MEETING_NO_MAX_RETRY; i++) {
            String meetingNo = String.valueOf(ThreadLocalRandom.current().nextInt(MEETING_NO_BASE, MEETING_NO_BASE + MEETING_NO_BOUND));
            if (meetingMapper.selectByMeetingNo(meetingNo) == null) {
                return meetingNo;
            }
        }
        throw exception(MEETING_NO_GENERATE_FAILED);
    }

    /** 同会议内昵称去重：重名追加 (2)、(3) … */
    private String deduplicateNickname(Long meetingId, String nickname) {
        if (attendeeMapper.selectByMeetingIdAndNickname(meetingId, nickname) == null) {
            return nickname;
        }
        for (int suffix = 2; suffix < 100; suffix++) {
            String candidate = nickname + "(" + suffix + ")";
            if (attendeeMapper.selectByMeetingIdAndNickname(meetingId, candidate) == null) {
                return candidate;
            }
        }
        return nickname + "-" + UUID.randomUUID().toString().substring(0, 4);
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 入会链接基地址：优先取配置 {@code tb.lecture.meeting.public-base-url}；
     * 留空时探测本机局域网 IPv4 并拼接前端端口，避免学生手机打不开 localhost。
     */
    private String resolvePublicBaseUrl() {
        String configured = meetingProperties.getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            String trimmed = configured.trim();
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        return "http://" + LanAddressUtils.detectLanIpv4() + ":" + meetingProperties.getFrontendPort();
    }

    /** 老师端实时接入信息：可发布令牌（能共享屏幕）。LiveKit 未配置时返回 null，控制台仍可查看历史 */
    private MeetingRealtimeVO buildHostRealtime(LectureMeetingDO meeting) {
        return buildRealtime(() -> liveKitTokenService.createHostToken(meeting.getMeetingNo()));
    }

    /** 学生端实时接入信息：只读令牌（只能订阅画面、可发 DataChannel 聊天） */
    private MeetingRealtimeVO buildAttendeeRealtime(LectureMeetingDO meeting, LectureMeetingAttendeeDO attendee) {
        return buildRealtime(() -> liveKitTokenService.createAttendeeToken(
                meeting.getMeetingNo(), attendee.getId(), attendee.getNickname()));
    }

    private MeetingRealtimeVO buildRealtime(Supplier<String> tokenSupplier) {
        if (!liveKitTokenService.isConfigured()) {
            log.warn("[buildRealtime][LiveKit 未配置，实时接入信息为空：请检查 LIVEKIT_API_KEY / LIVEKIT_API_SECRET]");
            return null;
        }
        MeetingRealtimeVO realtime = new MeetingRealtimeVO();
        realtime.setLivekitUrl(liveKitTokenService.resolveSignallingUrl());
        realtime.setLivekitToken(tokenSupplier.get());
        return realtime;
    }

    private MeetingInfoVO buildMeetingInfo(LectureMeetingDO meeting) {
        MeetingInfoVO infoVO = new MeetingInfoVO();
        infoVO.setId(meeting.getId());
        infoVO.setMeetingNo(meeting.getMeetingNo());
        infoVO.setTitle(meeting.getTitle());
        infoVO.setStatus(meeting.getStatus());
        infoVO.setStartTime(meeting.getStartTime());
        infoVO.setEndTime(meeting.getEndTime());
        // 台账口径的累计入会人数：在线人数由端上从 LiveKit 房间事件得出，后端不再冒充「在线」
        infoVO.setAttendeeCount(attendeeMapper.countByMeetingId(meeting.getId()));
        return infoVO;
    }

    private List<MeetingAttendeeVO> buildAttendeeList(Long meetingId) {
        List<MeetingAttendeeVO> list = new ArrayList<>();
        for (LectureMeetingAttendeeDO attendee : attendeeMapper.selectListByMeetingId(meetingId)) {
            MeetingAttendeeVO vo = new MeetingAttendeeVO();
            vo.setId(attendee.getId());
            vo.setNickname(attendee.getNickname());
            vo.setRole(attendee.getRole());
            vo.setJoinTime(attendee.getJoinTime());
            vo.setLeaveTime(attendee.getLeaveTime());
            list.add(vo);
        }
        return list;
    }

    private List<MeetingMessageVO> buildMessageList(Long meetingId) {
        List<MeetingMessageVO> list = new ArrayList<>();
        for (LectureMeetingMessageDO message : messageMapper.selectRecentByMeetingId(meetingId, meetingProperties.getHistoryLimit())) {
            list.add(buildMessageVO(message));
        }
        return list;
    }

    private MeetingMessageVO buildMessageVO(LectureMeetingMessageDO message) {
        MeetingMessageVO vo = new MeetingMessageVO();
        vo.setId(message.getId());
        vo.setAttendeeId(message.getAttendeeId());
        vo.setNickname(message.getNickname());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setKind(message.getKind());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

}
