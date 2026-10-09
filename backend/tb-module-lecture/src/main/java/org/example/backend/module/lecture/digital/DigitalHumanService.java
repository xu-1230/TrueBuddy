package org.example.backend.module.lecture.digital;

import org.example.backend.module.lecture.model.vo.DigitalSayReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayRespVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;

/**
 * AI 数字人 Service（后续二阶段一，见 {@code docs/17-AI数字人语音链路方案.md}）。
 *
 * <p>阶段一只做「固定文本 → CosyVoice 流式合成 → worker → LiveKit 音频轨」，不含 LLM 决策与 ASR。
 */
public interface DigitalHumanService {

    /**
     * 数字人可用性：前端据此决定是显示「数字人不可用」还是照常播报。
     *
     * @param meetingNo 会议号
     * @return 开关、worker 可达性与房间信息
     */
    DigitalStatusVO status(String meetingNo);

    /**
     * 让数字人在会议房间里播报一段文本。
     *
     * <p>同步语义：方法返回即代表音频已完整交给 worker（不代表已播完）。播报内容以数字人身份落消息表。
     *
     * @param reqVO 会议号、主持人凭据与文本
     * @return 播报统计与已落库的消息
     */
    DigitalSayRespVO say(DigitalSayReqVO reqVO);

    /**
     * 结束会议时请 worker 离开房间（best-effort，失败不抛）。
     *
     * @param meetingNo 会议号
     */
    void leaveRoom(String meetingNo);

}
