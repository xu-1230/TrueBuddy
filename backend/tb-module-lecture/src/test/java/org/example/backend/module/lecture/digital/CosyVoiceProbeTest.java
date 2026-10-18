package org.example.backend.module.lecture.digital;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次性探针（验完即删）：确认仓库现有的 DashScope Key 能否真的调用 CosyVoice 拿到裸 PCM。
 */
class CosyVoiceProbeTest {

    @Test
    void synthesizesPcm() throws Exception {
        String key = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "缺少 DASHSCOPE_API_KEY，跳过真实合成探针");
        Constants.apiKey = key;

        String model = System.getProperty("probe.model", "cosyvoice-v3-flash");
        String voice = System.getProperty("probe.voice", "longanyang");
        var pcm = new ByteArrayOutputStream();
        var latch = new CountDownLatch(1);
        var failure = new AtomicReference<Exception>();
        var firstChunkMs = new AtomicLong(-1);
        long began = System.nanoTime();

        var param = SpeechSynthesisParam.builder()
                .model(model)
                .voice(voice)
                .format(SpeechSynthesisAudioFormat.PCM_48000HZ_MONO_16BIT)
                .build();

        var synthesizer = new SpeechSynthesizer(param, new ResultCallback<SpeechSynthesisResult>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                ByteBuffer frame = result.getAudioFrame();
                if (frame == null) {
                    return;
                }
                firstChunkMs.compareAndSet(-1, (System.nanoTime() - began) / 1_000_000);
                byte[] bytes = new byte[frame.remaining()];
                frame.get(bytes);
                pcm.writeBytes(bytes);
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

        synthesizer.streamingCall("同学们好，这是一段用于验证语音链路的测试播报。");
        synthesizer.streamingComplete();
        Assumptions.assumeTrue(latch.await(60, TimeUnit.SECONDS), "60s 内未收到完成回调");

        System.out.println("[probe] model=" + model + " voice=" + voice
                + " error=" + (failure.get() == null ? "none" : failure.get().getMessage())
                + " firstChunkMs=" + firstChunkMs.get()
                + " sdkFirstPkgDelayMs=" + synthesizer.getFirstPackageDelay()
                + " bytes=" + pcm.size()
                + " durationMs=" + (pcm.size() * 1000L / (48000L * 2)));

        if (pcm.size() > 0) {
            Files.write(Path.of("target/probe.pcm"), pcm.toByteArray());
            Files.writeString(Path.of("target/probe.txt"),
                    model + "\n" + voice + "\n" + pcm.size() + "\n" + firstChunkMs.get() + "\n");
        }
        if (failure.get() != null) {
            throw new IllegalStateException("合成失败: " + failure.get().getMessage(), failure.get());
        }
        org.junit.jupiter.api.Assertions.assertTrue(pcm.size() > 1000, "PCM 太少，疑似未真正合成: " + pcm.size());
    }
}
