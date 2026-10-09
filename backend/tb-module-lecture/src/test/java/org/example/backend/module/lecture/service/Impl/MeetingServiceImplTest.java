package org.example.backend.module.lecture.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.lecture.config.MeetingProperties;
import org.example.backend.module.lecture.livekit.LiveKitRoomServiceClient;
import org.example.backend.module.lecture.livekit.LiveKitTokenService;
import org.example.backend.module.lecture.mapper.LectureMeetingAttendeeMapper;
import org.example.backend.module.lecture.mapper.LectureMeetingMapper;
import org.example.backend.module.lecture.mapper.LectureMeetingMessageMapper;
import org.example.backend.module.lecture.model.LectureMeetingAttendeeDO;
import org.example.backend.module.lecture.model.LectureMeetingDO;
import org.example.backend.module.lecture.model.vo.MeetingAttendeeVO;
import org.example.backend.module.lecture.model.vo.MeetingCloseReqVO;
import org.example.backend.module.lecture.model.vo.MeetingDetailRespVO;
import org.example.backend.module.lecture.model.vo.MeetingInfoVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinReqVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinRespVO;
import org.example.backend.module.lecture.model.vo.MeetingLeaveReqVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_ALREADY_CLOSED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_ATTENDEE_TOKEN_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会议生命周期的回归锁定（Service 接缝）。
 *
 * <p>为什么锁在这一层：本模块唯一的运行时风险是「入会 / 离会 / 结束」三个动作的判定依据，
 * 而它们的错误表现（学生进不来、名单虚高、会议关不掉）在现场演示时排查成本极高。
 *
 * <p>为什么不打桩数据库：全仓无独立测试库，而 docs/11 §2.2 禁止污染 {@code tb} 生产库，
 * 因此这里只 mock mapper，不启动 Spring 上下文。代价是 **mapper 里 SQL 文本的正确性
 * （{@code WHERE leave_time IS NULL}、{@code deleted = FALSE}）不在本测试覆盖范围内**，
 * 由 docs/16 §11.2 的手工联调与逐字 review 兜住。
 */
@ExtendWith(MockitoExtension.class)
class MeetingServiceImplTest {

    private static final String MEETING_NO = "123456";
    private static final Long MEETING_ID = 1L;

    @Mock
    private LectureMeetingMapper meetingMapper;
    @Mock
    private LectureMeetingAttendeeMapper attendeeMapper;
    @Mock
    private LectureMeetingMessageMapper messageMapper;
    @Mock
    private MeetingProperties meetingProperties;
    @Mock
    private LiveKitTokenService liveKitTokenService;
    @Mock
    private LiveKitRoomServiceClient liveKitRoomServiceClient;

    @InjectMocks
    private MeetingServiceImpl meetingService;

    private LectureMeetingDO runningMeeting() {
        LectureMeetingDO meeting = new LectureMeetingDO();
        meeting.setId(MEETING_ID);
        meeting.setMeetingNo(MEETING_NO);
        meeting.setTitle("答辩演练");
        meeting.setHostToken("host-token");
        meeting.setStatus(1);
        return meeting;
    }

    private LectureMeetingAttendeeDO attendee(long id, String nickname) {
        LectureMeetingAttendeeDO attendee = new LectureMeetingAttendeeDO();
        attendee.setId(id);
        attendee.setMeetingId(MEETING_ID);
        attendee.setNickname(nickname);
        attendee.setAttendeeToken("token-" + id);
        attendee.setRole(2);
        return attendee;
    }

    /** VO 的字段名集合——用来断言「在线」这类字段已从响应契约里消失 */
    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
    }

    private MeetingJoinReqVO joinReq(String nickname) {        MeetingJoinReqVO reqVO = new MeetingJoinReqVO();
        reqVO.setMeetingNo(MEETING_NO);
        reqVO.setNickname(nickname);
        return reqVO;
    }

    @Test
    @DisplayName("入会不看人数：台账已有 25 行时第 26 个学生仍应成功入会")
    void joinSucceedsRegardlessOfLedgerSize() {
        // 台账里 25 行「尚未显式离开」的记录——这正是旧闸门会误判为「25 人在线」而拒绝入会的数据
        List<LectureMeetingAttendeeDO> ledger = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            ledger.add(attendee(i, "学生" + i));
        }

        when(meetingMapper.selectByMeetingNo(MEETING_NO)).thenReturn(runningMeeting());
        when(liveKitTokenService.isConfigured()).thenReturn(true);
        when(meetingProperties.getNicknameMaxLength()).thenReturn(20);
        when(attendeeMapper.selectByMeetingIdAndNickname(any(), any())).thenReturn(null);
        when(attendeeMapper.insert(any(LectureMeetingAttendeeDO.class))).thenAnswer(invocation -> {
            LectureMeetingAttendeeDO inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        });
        when(attendeeMapper.selectListByMeetingId(MEETING_ID)).thenReturn(ledger);
        when(attendeeMapper.countByMeetingId(MEETING_ID)).thenReturn(25);
        when(messageMapper.selectRecentByMeetingId(any(), anyInt())).thenReturn(List.of());
        when(liveKitTokenService.resolveSignallingUrl()).thenReturn("ws://192.168.1.5:7880");
        when(liveKitTokenService.createAttendeeToken(anyString(), any(), anyString())).thenReturn("attendee-token");

        MeetingJoinRespVO respVO = meetingService.join(joinReq("小明"));

        assertNotNull(respVO.getAttendeeToken(), "入会必须拿到参会凭据");
        assertEquals(99L, respVO.getAttendeeId());
        // 累计入会名单是台账口径（mapper 桩返回的 25 行），不是「在线」口径
        assertEquals(25, respVO.getMeeting().getAttendeeCount());
        verify(attendeeMapper).insert(any(LectureMeetingAttendeeDO.class));
    }

    @Test
    @DisplayName("离会：凭据有效时给台账那一行补记离会时间")
    void leaveWritesLeaveTimeOnLedgerRow() {
        when(meetingMapper.selectByMeetingNo(MEETING_NO)).thenReturn(runningMeeting());
        when(attendeeMapper.selectByAttendeeToken("token-7")).thenReturn(attendee(7L, "小明"));
        when(attendeeMapper.markLeft(7L)).thenReturn(1);

        meetingService.leave(leaveReq("token-7"));

        verify(attendeeMapper).markLeft(7L);
    }

    @Test
    @DisplayName("离会：重复调用幂等——第二次不报错，也不会覆盖首次时间")
    void leaveIsIdempotent() {
        when(meetingMapper.selectByMeetingNo(MEETING_NO)).thenReturn(runningMeeting());
        when(attendeeMapper.selectByAttendeeToken("token-7")).thenReturn(attendee(7L, "小明"));
        // mapper 的 WHERE leave_time IS NULL 让第二次影响 0 行：Service 不得因此报错
        when(attendeeMapper.markLeft(7L)).thenReturn(0);

        meetingService.leave(leaveReq("token-7"));
        meetingService.leave(leaveReq("token-7"));

        verify(attendeeMapper, times(2)).markLeft(7L);
    }

    @Test
    @DisplayName("离会：跨会议的参会凭据按无效处理（错误码 1_007_000_009）")
    void leaveRejectsTokenFromAnotherMeeting() {
        LectureMeetingAttendeeDO foreign = attendee(7L, "小明");
        foreign.setMeetingId(999L);
        when(meetingMapper.selectByMeetingNo(MEETING_NO)).thenReturn(runningMeeting());
        when(attendeeMapper.selectByAttendeeToken("token-7")).thenReturn(foreign);

        ServiceException ex = assertThrows(ServiceException.class, () -> meetingService.leave(leaveReq("token-7")));
        assertEquals(MEETING_ATTENDEE_TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("离会：会议已结束时返回可读错误（1_007_000_002）而不是 500")
    void leaveRejectsClosedMeeting() {
        LectureMeetingDO closed = runningMeeting();
        closed.setStatus(2);
        when(meetingMapper.selectByMeetingNo(MEETING_NO)).thenReturn(closed);

        ServiceException ex = assertThrows(ServiceException.class, () -> meetingService.leave(leaveReq("token-7")));
        assertEquals(MEETING_ALREADY_CLOSED.getCode(), ex.getCode());
    }

    private MeetingLeaveReqVO leaveReq(String attendeeToken) {
        MeetingLeaveReqVO reqVO = new MeetingLeaveReqVO();
        reqVO.setMeetingNo(MEETING_NO);
        reqVO.setAttendeeToken(attendeeToken);
        return reqVO;
    }

    @Test
    @DisplayName("响应契约：成员与会议信息里不再有 online / onlineCount 字段")
    void detailResponseCarriesNoOnlineField() {
        LectureMeetingAttendeeDO stayed = attendee(1L, "小明");
        LectureMeetingAttendeeDO left = attendee(2L, "小红");
        left.setLeaveTime(LocalDateTime.now());

        when(meetingMapper.selectByMeetingNo(MEETING_NO)).thenReturn(runningMeeting());
        when(attendeeMapper.selectListByMeetingId(MEETING_ID)).thenReturn(List.of(stayed, left));
        when(attendeeMapper.countByMeetingId(MEETING_ID)).thenReturn(2);
        when(messageMapper.selectRecentByMeetingId(any(), anyInt())).thenReturn(List.of());
        when(meetingProperties.getHistoryLimit()).thenReturn(50);

        MeetingDetailRespVO respVO = meetingService.detail(MEETING_NO, "host-token");

        // 「在线」不再是后端能说的话：VO 里连字段都没有，端上才无从误用
        assertFalse(fieldNames(MeetingInfoVO.class).contains("onlineCount"), "会议信息不应再有 onlineCount");
        assertFalse(fieldNames(MeetingAttendeeVO.class).contains("online"), "成员不应再有 online 布尔");
        assertTrue(fieldNames(MeetingAttendeeVO.class).contains("leaveTime"), "台账应暴露 leaveTime");
        assertEquals(2, respVO.getMeeting().getAttendeeCount(), "attendeeCount 是累计入会台账口径");
        assertEquals(2, respVO.getAttendees().size());
        assertNotNull(respVO.getAttendees().get(1).getLeaveTime(), "已离开的行要带离会时间");
        assertNull(respVO.getAttendees().get(0).getLeaveTime(), "尚未显式离开的行 leaveTime 为空（≠ 在线）");
    }

    @Test
    @DisplayName("结束会议：给台账补记离会时间，而不是把 online 置假")
    void closeMarksLedgerLeft() {
        MeetingCloseReqVO reqVO = new MeetingCloseReqVO();
        reqVO.setMeetingNo(MEETING_NO);
        reqVO.setHostToken("host-token");

        when(meetingMapper.selectByMeetingNo(MEETING_NO)).thenReturn(runningMeeting());

        meetingService.close(reqVO);

        verify(attendeeMapper).markAllLeft(MEETING_ID);
        verify(liveKitRoomServiceClient).deleteRoom(MEETING_NO);
    }

}
