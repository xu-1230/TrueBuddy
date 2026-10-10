package org.example.backend.module.lecture.digital.worker;

import java.io.InputStream;

/**
 * 数字人 worker 的调用接缝（契约见 {@code docs/17} §5.1 / §5.2）。
 *
 * <p>worker 是唯一能把 PCM 变成 LiveKit 音频轨的东西（LiveKit 没有 JVM 服务端媒体 SDK），
 * 后端只负责合成与调度，因此这里全部是「本机 HTTP + 裸流」，不引入任何实时依赖。
 */
public interface DigitalHumanWorkerClient {

    /**
     * worker 是否可用（前端要据此显示「数字人不可用」，而不是静默无声）。
     */
    boolean healthy();

    /**
     * 让 worker 以数字人身份进房间并常驻一条音频轨。同一房间重复调用应为幂等。
     *
     * @param room       房间名（{@code meeting-{meetingNo}}）
     * @param livekitUrl 信令地址
     * @param agentToken 后端刚签发的数字人访问令牌
     */
    void openSession(String room, String livekitUrl, String agentToken);

    /**
     * 结束会议时调用：worker 离开房间。失败只记日志，不影响会议关闭。
     */
    void closeSession(String room);

    /**
     * 把 PCM 作为请求体<b>流式</b>推给 worker（chunked，不 base64、不封 WAV）。
     *
     * <p>本方法阻塞到 worker 收下全部字节并返回；{@code pcm} 由另一个线程边合成边喂。
     *
     * @param room      房间名
     * @param requestId 播报幂等标识
     * @param pcm       48kHz / 单声道 / 16bit 裸 PCM
     */
    void streamPcm(String room, String requestId, InputStream pcm);

    /**
     * 取消一条在途播报：worker 置 cancelled 并清空未播队列，本方法对应的那次 {@link #streamPcm} 随即返回。
     *
     * <p>幂等：worker 侧不存在该 requestId 时返回 false（已经播完或从未开始），调用方不应报错。
     *
     * @param requestId 播报幂等标识
     * @return worker 是否确认收到取消
     */
    boolean cancelSay(String requestId);
}
