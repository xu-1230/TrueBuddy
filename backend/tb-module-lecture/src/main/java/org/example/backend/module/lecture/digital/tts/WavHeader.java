package org.example.backend.module.lecture.digital.tts;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 裸 PCM 封 WAV 头（试听通路用，见 {@code docs/18} §5.3）。
 *
 * <p>浏览器 {@code <audio>} 不认 {@code audio/L16} 裸流，只认带容器的音频；而合成侧（CosyVoice）
 * 与 worker 侧都刻意用<b>裸 PCM</b>（LiveKit 内部就是 48kHz，加头反而要多剥一层）。所以只有试听这一条路
 * 需要在出口补 44 字节标准头，其余链路保持不变。
 */
public final class WavHeader {

    /** PCM 采样率：与 {@code CosyVoiceTtsSynth} 的输出、worker 的校验严格成对 */
    private static final int SAMPLE_RATE = 48000;
    /** 声道数 */
    private static final int CHANNELS = 1;
    /** 位深 */
    private static final int BITS_PER_SAMPLE = 16;

    private WavHeader() {
    }

    /**
     * 写入 44 字节 canonical WAV 头，声头参数固定为 48kHz / 单声道 / 16bit。
     *
     * @param out        目标流（不关闭）
     * @param dataLength PCM 字节数，决定 {@code data} 块长度
     */
    public static void write(OutputStream out, int dataLength) throws IOException {
        int byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;

        out.write(ascii("RIFF"));
        out.write(le32(36 + dataLength));
        out.write(ascii("WAVE"));
        out.write(ascii("fmt "));
        out.write(le32(16));
        out.write(le16(1));
        out.write(le16(CHANNELS));
        out.write(le32(SAMPLE_RATE));
        out.write(le32(byteRate));
        out.write(le16(blockAlign));
        out.write(le16(BITS_PER_SAMPLE));
        out.write(ascii("data"));
        out.write(le32(dataLength));
    }

    private static byte[] ascii(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] le32(int value) {
        return new byte[]{(byte) (value & 0xFF), (byte) ((value >>> 8) & 0xFF),
                (byte) ((value >>> 16) & 0xFF), (byte) ((value >>> 24) & 0xFF)};
    }

    private static byte[] le16(int value) {
        return new byte[]{(byte) (value & 0xFF), (byte) ((value >>> 8) & 0xFF)};
    }
}
