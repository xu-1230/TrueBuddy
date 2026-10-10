package org.example.backend.module.lecture.ppt.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.lecture.digital.DigitalHumanService;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeRespVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechSpeakReqVO;
import org.example.backend.module.lecture.ppt.service.PptSpeechService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * PPT 讲稿生成接口（前端 `/index/ppt` 的两个 AI 按钮都打到这里）。
 *
 * <p>一个接口覆盖两个按钮：请求里 {@code pages} 长度 1 是「优化本页」，长度 N 是「优化全部」。
 * 整篇是**一次请求**（后端内部按批调用模型），前端因此不需要为「全部」写一套循环。
 *
 * <p>放行：{@code /ppt/**} 已在 {@code tb.security.permit-all-urls} 中，与 {@code /meeting/**} 同理
 * （PPT 页目前不依赖登录态）。
 */
@RestController
@RequestMapping("/ppt/speech")
public class PptSpeechController {

    @Resource
    private PptSpeechService pptSpeechService;
    /** 试听走的是合成通路（{@code digital} 包），与「发进房间的播报」共用同一个 TTS 接缝 */
    @Resource
    private DigitalHumanService digitalHumanService;

    /**
     * 生成讲稿。
     *
     * <p>部分失败不报错：响应里的 {@code failedIndexes} 标明未生成的页码，前端标红并可只重试这些页。
     */
    @PostMapping("/optimize")
    public CommonResult<PptSpeechOptimizeRespVO> optimize(@RequestBody PptSpeechOptimizeReqVO reqVO) {
        return success(pptSpeechService.optimize(reqVO));
    }

    /**
     * 试听：把这页讲稿合成成 WAV 直接回给浏览器，<b>不进 LiveKit 房间、不依赖 worker</b>。
     *
     * <p>返回的是音频字节而非 {@code CommonResult}，与 {@code POST /chat} 同属已记录的例外
     * （见 {@code docs/05} §2.1）。合成失败发生在写出第一个字节<b>之前</b>，所以错误照常走 JSON；
     * 一旦开始写流就只有客户端断开这一条退路（前端用 {@code AbortController}）。
     */
    @PostMapping("/speak")
    public void speak(@RequestBody PptSpeechSpeakReqVO reqVO, HttpServletResponse response) throws IOException {
        response.setContentType("audio/wav");
        response.setHeader("Cache-Control", "no-store");
        digitalHumanService.speak(reqVO.getText(), response.getOutputStream());
    }

}
