package org.example.backend.module.lecture.service;

import org.example.backend.module.lecture.model.LectureMeetingAttendeeDO;
import org.example.backend.module.lecture.model.vo.MeetingCloseReqVO;
import org.example.backend.module.lecture.model.vo.MeetingCreateReqVO;
import org.example.backend.module.lecture.model.vo.MeetingCreateRespVO;
import org.example.backend.module.lecture.model.vo.MeetingDetailRespVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinReqVO;
import org.example.backend.module.lecture.model.vo.MeetingJoinRespVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageReqVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageVO;

/**
 * 模拟会议 Service
 *
 * <p>老师走控制台（{@code hostToken} 鉴权），学生点入会链接后弹窗输入名字入会（{@code attendeeToken} 标识身份）。
 *
 * <p><b>实时媒体（屏幕共享）与聊天都由 LiveKit 承担</b>（屏幕共享走音视频轨、聊天走 DataChannel），
 * 后端不参与实时转发，只做四件事：会议模型落库、签发访问令牌、消息落库、结束会议时删房间。
 *
 * <p>返回值中的 {@code LectureMeetingAttendeeDO} 仅供模块内部（凭据校验）使用，不外露给 HTTP 接口层。
 */
public interface MeetingService {

    /**
     * 创建会议：生成 6 位会议号、主持人凭据与学生入会链接。
     *
     * @param reqVO 会议标题
     * @return 会议号、入会链接与主持人凭据（凭据仅返回一次）
     */
    MeetingCreateRespVO create(MeetingCreateReqVO reqVO);

    /**
     * 学生入会：校验会议可入、昵称合法、人数未满，落参会人并签发参会凭据。
     *
     * @param reqVO 会议号与昵称
     * @return 参会凭据、会议信息、参会人列表、最近消息与实时接入信息
     */
    MeetingJoinRespVO join(MeetingJoinReqVO reqVO);

    /**
     * 会议详情（老师控制台初始化 / 刷新后恢复），响应里带**可发布**的实时接入信息。
     *
     * @param meetingNo 会议号
     * @param hostToken 主持人凭据
     * @return 会议信息、参会人列表、最近消息与实时接入信息
     */
    MeetingDetailRespVO detail(String meetingNo, String hostToken);

    /**
     * 结束会议：置为已结束、写结束时间、把全部在线参会人置为离线，并删除 LiveKit 房间。
     *
     * @param reqVO 会议号与主持人凭据
     */
    void close(MeetingCloseReqVO reqVO);

    /**
     * 学生侧会议状态：校验参会凭据后返回会议信息、成员与消息，并带**只读**的实时接入信息。
     *
     * <p>用于学生端刷新页面或断线重连后补齐数据（学生不持有 {@code hostToken}，不能走 {@link #detail}）。
     *
     * @param meetingNo     会议号
     * @param attendeeToken 参会凭据
     * @return 会议信息、参会人列表、最近消息与实时接入信息
     */
    MeetingDetailRespVO state(String meetingNo, String attendeeToken);

    /**
     * 按参会凭据查询参会人（未找到返回 null，供学生侧状态与消息落库校验发送者）。
     */
    LectureMeetingAttendeeDO getAttendeeByToken(String attendeeToken);

    /**
     * 消息落库（聊天实时走 LiveKit DataChannel，本方法只负责持久化）。
     *
     * <p>发送者身份由凭据解析：{@code hostToken} 命中即为老师发言，否则按 {@code attendeeToken} 定位学生。
     *
     * @param reqVO 会议号、凭据与正文
     * @return 已落库的消息（含数据库 id 与创建时间，供前端回看核对）
     */
    MeetingMessageVO postMessage(MeetingMessageReqVO reqVO);

    /**
     * 落库并返回一条消息（{@link #postMessage} 与后续「服务端替数字人发言」共用）。
     *
     * @param meetingId  会议 id
     * @param attendeeId 发送人 id（老师为 null）
     * @param nickname   发送人昵称
     * @param role       发送人角色：1=老师，2=学生
     * @param content    消息正文
     * @return 已落库的消息
     */
    MeetingMessageVO sendMessage(Long meetingId, Long attendeeId, String nickname, Integer role, String content);

}
