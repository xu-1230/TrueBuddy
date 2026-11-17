package org.example.backend.module.lecture.digital.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CosyVoice v3 流式合成（DashScope 原生 WebSocket）。
 *
 * <p>输出刻意选 {@code PCM_48000HZ_MONO_16BIT}：LiveKit 内部工作速率就是 48kHz，
 * 选 22050 会在 worker 侧多一次重采样（见 {@code docs/17} §6）。
 */
@Slf4j
@Component
public class CosyVoiceTtsSynth implements TtsSynthPort {

    /** 单次合成的等待上限；超时说明厂商侧没回音 */
    private static final int AWAIT_TIMEOUT_SECONDS = 60;

    private final DigitalHumanProperties properties;

    public CosyVoiceTtsSynth(DigitalHumanProperties properties) {
        this.properties = properties;
    }

    @Override
    public TtsStats synthesize(String text, String voice, String model, OutputStream pcm) {
        String apiKey = properties.getDashscopeApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("数字人语音未配置 DashScope API Key");
        }
        String actualVoice = voice == null || voice.isBlank() ? properties.getTtsVoice() : voice;
        String actualModel = model == null || model.isBlank() ? properties.getTtsModel() : model;

        var firstChunkMs = new AtomicLong(-1);
        var bytes = new AtomicLong();
        var failure = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        long began = System.nanoTime();

        var param = SpeechSynthesisParam.builder()
                .model(actualModel)
                .voice(actualVoice)
                .format(SpeechSynthesisAudioFormat.PCM_48000HZ_MONO_16BIT)
                .apiKey(apiKey)
                .build();

        var synthesizerRef = new AtomicReference<SpeechSynthesizer>();
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, new ResultCallback<SpeechSynthesisResult>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                ByteBuffer frame = result.getAudioFrame();
                if (frame == null) {
                    return;
                }
                firstChunkMs.compareAndSet(-1, (System.nanoTime() - began) / 1_000_000);
                byte[] chunk = new byte[frame.remaining()];
                frame.get(chunk);
                try {
                    pcm.write(chunk);
                    bytes.addAndGet(chunk.length);
                } catch (IOException e) {
                    // 下游（worker）断开：不再继续要音频，取消合成避免继续计费
                    failure.set(e);
                    latch.countDown();
                    cancelQuietly(synthesizerRef.get());
                }
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                failure.set(e);
                latch.countDown();
            }
        });

        synthesizerRef.set(synthesizer);
        synthesizer.streamingCall(text);
        synthesizer.streamingComplete();

        boolean finished;
        try {
            finished = latch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synthesizer.streamingCancel();
            throw new IllegalStateException("语音合成被中断", e);
        }
        if (!finished) {
            synthesizer.streamingCancel();
            throw new IllegalStateException("语音合成超时（" + AWAIT_TIMEOUT_SECONDS + "s 未结束）");
        }
        if (failure.get() != null) {
            throw new IllegalStateException("语音合成失败: " + failure.get().getMessage(), failure.get());
        }
        long total = bytes.get();
        if (total <= 0) {
            throw new IllegalStateException("语音合成没有产出任何音频");
        }
        log.info("[synthesize][合成完成 model={}, voice={}, firstChunkMs={}, bytes={}]",
                actualModel, actualVoice, firstChunkMs.get(), total);
        return new TtsStats(firstChunkMs.get(), total);
    }

    private static void cancelQuietly(SpeechSynthesizer synthesizer) {
        if (synthesizer == null) {
            return;
        }
        try {
            synthesizer.streamingCancel();
        } catch (RuntimeException ignored) {
            // 取消失败没有补救手段：连接本身已经出问题，回调方会带着原因抛出
        }
    }
}
