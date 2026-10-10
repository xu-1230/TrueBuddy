package org.example.backend.module.lecture.digital;

import org.example.backend.module.lecture.model.vo.DigitalCancelReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayRespVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;

import java.io.OutputStream;

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

    /**
     * 取消一条在途播报（老师侧动作，用 {@code hostToken} 鉴权）。
     *
     * <p>语义是「让 worker 停止推送这段并清空未播队列」，对应那次阻塞中的 {@code say} 会很快返回。
     * 幂等：worker 侧没有该 requestId（已播完）时返回 false，不报错。
     *
     * @param reqVO 会议号、主持人凭据与要取消的 requestId
     * @return 是否真的取消到了一段在途播报
     */
    boolean cancelSay(DigitalCancelReqVO reqVO);

    /**
     * 试听：把一段文本合成成 WAV 写进出参流，<b>不进房间、不依赖 worker</b>（见 {@code docs/18} §5.3）。
     *
     * <p>放映形态（彩排）用这条路，好处是 worker 没起来、LiveKit 没配也能听效果，且不占参会席位。
     *
     * @param text 要合成的文本，上限 {@code tb.lecture.digital-human.max-speak-chars}
     * @param out  目标流（44 字节 WAV 头 + 48kHz/单声道/16bit PCM；本方法不关闭该流）
     */
    void speak(String text, OutputStream out);

}
