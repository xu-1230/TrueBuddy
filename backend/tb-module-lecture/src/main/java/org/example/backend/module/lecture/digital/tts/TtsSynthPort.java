package org.example.backend.module.lecture.digital.tts;

import java.io.OutputStream;

/**
 * 文本 → 裸 PCM 的流式合成接缝（见 {@code docs/17-AI数字人语音链路方案.md} §10）。
 *
 * <p>之所以包这一层：DashScope SDK 是静态全局 Key + 回调式流，直接散进业务代码会让播报链路没法离线测试。
 * 单测里换一个假实现即可，真实厂商与参数只影响本接口的实现类。
 */
public interface TtsSynthPort {

    /**
     * 把 {@code text} 合成为 48kHz / 单声道 / 16bit 裸 PCM，边到边写进 {@code pcm}。
     *
     * <p>本方法<b>阻塞</b>到合成结束；调用方需在另一线程消费 {@code pcm}，否则管道缓冲写满会死锁。
     *
     * @param text  待播文本
     * @param voice 音色编码（内置音色或复刻音色的 voice_id）
     * @param model TTS 模型（内置音色用全局配置；复刻音色用创建时的模型）
     * @param pcm   PCM 落点，由调用方负责关闭
     * @return 合成统计
     * @throws IllegalStateException 厂商报错、超时或一个字节都没合成出来
     */
    TtsStats synthesize(String text, String voice, String model, OutputStream pcm);

    /**
     * @param firstChunkMs 首个音频块到达耗时（毫秒），-1 表示没拿到
     * @param bytes        合成出的 PCM 总字节数
     */
    record TtsStats(long firstChunkMs, long bytes) {

        /** PCM 时长（毫秒）：48000Hz × 单声道 × 16bit = 96000 字节/秒 */
        public long durationMs() {
            return bytes * 1000L / 96_000L;
        }
    }
}
