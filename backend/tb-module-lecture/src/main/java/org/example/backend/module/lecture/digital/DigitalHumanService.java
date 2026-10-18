package org.example.backend.module.lecture.digital;

import org.example.backend.module.lecture.model.vo.DigitalSpeakReqVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 数字人播报 Service。
 *
 * <p>声音**不经过任何中间进程**：后端把 CosyVoice 的 PCM 边合成边以 SSE 推给**发起请求的那个浏览器**
 * （老师放映 PPT 的那一页），由该页播放；那一页正被屏幕共享，声音因此随共享轨一起到学生端。
 * 旧的两段式中继（Node worker 进房间、发一条数字人音频轨）已删除，原因见 {@code docs/16} §5.7。
 */
public interface DigitalHumanService {

    /**
     * 数字人可用性：前端据此显示「语音不可用」，而不是点了播报却静默无声。
     *
     * <p>不再探测任何进程（worker 已删）：只看开关与 DashScope 合成配置是否齐备。
     *
     * @return 开关、是否可用
     */
    DigitalStatusVO status();

    /**
     * 流式播报：把文本合成为 48kHz / 单声道 / 16bit PCM，边产出边以 SSE 事件推给调用方。
     *
     * <p>事件协议（{@code docs/16} §5.7）：{@code status}（started，含采样率）→ 若干 {@code chunk}
     * （base64 PCM）→ {@code status}（done，含字节数与首包耗时）；合成失败发 {@code error}。
     * 调用方断开连接即取消合成——底层把「写不出去」映射成 IOException，合成实现据此停止继续取音频。
     *
     * @param reqVO 文本与（可空的）会议信息
     * @return SSE 事件流；校验不通过时同步抛业务异常（此时返回的是 JSON 错误体，不是事件流）
     */
    SseEmitter speak(DigitalSpeakReqVO reqVO);

}
