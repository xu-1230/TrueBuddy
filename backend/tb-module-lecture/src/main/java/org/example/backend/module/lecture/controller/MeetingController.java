package org.example.backend.module.lecture.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.lecture.digital.DigitalHumanService;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;
import org.example.backend.module.lecture.model.vo.MeetingCloseReqVO;
import org.example.backend.module.lecture.model.vo.MeetingCreateReqVO;
import org.example.backend.module.lecture.model.vo.MeetingCreateRespVO;
import org.example.backend.module.lecture.model.vo.MeetingDetailRespVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinReqVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinRespVO;
import org.example.backend.module.lecture.model.vo.MeetingLeaveReqVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageReqVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageVO;
import org.example.backend.module.lecture.service.MeetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 模拟会议接口。
 *
 * <p>老师走控制台（创建 / 详情 / 结束，用 {@code hostToken} 鉴权），学生点入会链接后弹窗输入名字入会。
 * 实时媒体（屏幕共享）与聊天走 <b>LiveKit</b>：入会 / 详情 / 状态接口下发 {@code realtime} 接入信息，
 * 后端不参与实时转发。
 *
 * <p>免登录：{@code /meeting/**} 已在 {@code tb.security.permit-all-urls} 放行，
 * 会议内身份不依赖会话级 Token。
 */
@RestController
@RequestMapping("/meeting")
public class MeetingController {

    @Resource
    private MeetingService meetingService;
    @Resource
    private DigitalHumanService digitalHumanService;

    /** 创建会议：返回会议号、学生入会链接与主持人凭据（凭据仅返回一次） */
    @PostMapping("/create")
    public CommonResult<MeetingCreateRespVO> create(@RequestBody MeetingCreateReqVO reqVO) {
        return success(meetingService.create(reqVO));
    }

    /** 学生入会：会议号 + 昵称，返回参会凭据与会议初始数据 */
    @PostMapping("/join")
    public CommonResult<MeetingJoinRespVO> join(@RequestBody MeetingJoinReqVO reqVO) {
        return success(meetingService.join(reqVO));
    }

    /**
     * 学生离会：给参会台账补记离会时间。
     *
     * <p>由学生端在页面关闭前用 {@code sendBeacon} 触发；重复调用幂等。
     * 本接口<b>不</b>维护「在线」状态——在线与否只看 LiveKit 房间事件。
     */
    @PostMapping("/leave")
    public CommonResult<Boolean> leave(@RequestBody MeetingLeaveReqVO reqVO) {
        meetingService.leave(reqVO);
        return success(true);
    }

    /** 会议详情：老师控制台初始化 / 刷新后恢复 */
    @GetMapping("/detail")
    public CommonResult<MeetingDetailRespVO> detail(@RequestParam("meetingNo") String meetingNo,
                                                    @RequestParam("hostToken") String hostToken) {
        return success(meetingService.detail(meetingNo, hostToken));
    }

    /** 学生侧会议状态：刷新页面或断线重连后补齐成员与消息 */
    @GetMapping("/state")
    public CommonResult<MeetingDetailRespVO> state(@RequestParam("meetingNo") String meetingNo,
                                                   @RequestParam("attendeeToken") String attendeeToken) {
        return success(meetingService.state(meetingNo, attendeeToken));
    }

    /**
     * 消息落库：聊天实时走 LiveKit 的 DataChannel（不经后端），本接口只把消息写进历史。
     *
     * <p>调用方在发送 DataChannel 之后串行调用本接口；失败只影响历史，不影响实时。
     */
    @PostMapping("/message")
    public CommonResult<MeetingMessageVO> message(@RequestBody MeetingMessageReqVO reqVO) {
        return success(meetingService.postMessage(reqVO));
    }

    /** 结束会议：删除 LiveKit 房间（学生端立即断开）并把会议置为已结束 */
    @PostMapping("/close")
    public CommonResult<Boolean> close(@RequestBody MeetingCloseReqVO reqVO) {
        meetingService.close(reqVO);
        return success(true);
    }

    /**
     * 数字人可用性：前端据此显示「语音不可用」，而不是点了播报却静默无声。
     *
     * <p>播报本身不在这里：它由放映页（{@code /index/ppt}）直接向 {@code POST /ppt/speech/speak}
     * 取流播放（声音随共享轨到学生端），见 docs/16 §5.7。
     */
    @GetMapping("/digital/status")
    public CommonResult<DigitalStatusVO> digitalStatus() {
        return success(digitalHumanService.status());
    }

}
