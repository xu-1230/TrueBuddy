package org.example.backend.module.lecture.ppt.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.lecture.digital.DigitalHumanService;
import org.example.backend.module.lecture.model.vo.DigitalSpeakReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateRespVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeRespVO;
import org.example.backend.module.lecture.ppt.service.PptSpeechService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * PPT 讲稿接口（前端 `/index/ppt` 都打到这里）。
 *
 * <ul>
 *   <li>{@code /optimize}：讲稿生成。一个接口覆盖两个按钮——请求里 {@code pages} 长度 1 是「优化本页」，
 *       长度 N 是「优化全部」；整篇是**一次请求**（后端内部按批调用模型），前端因此不需要为「全部」写一套循环。</li>
 *   <li>{@code /narrate}：AI 现场讲词。讲课工作项的 ai 模式逐页调用：根据「备注（蓝本）+ 页面信息 +
 *       现场进度」生成一段口播文本，普通 JSON（不需要 SSE——它不是音频，音频仍走 {@code /speak}）。
 *       口播是一次性派生物，只进台账、不写回 remark。</li>
 *   <li>{@code /speak}：讲稿播报。PCM 边合成边以 SSE 事件下发，**由放映页本页播放**——这一页正被屏幕共享，
 *       声音因此随共享轨一起到学生端（见 docs/16 §5.7）。</li>
 * </ul>
 *
 * <p>放行：{@code /ppt/**} 已在 {@code tb.security.permit-all-urls} 中，与 {@code /meeting/**} 同理
 * （PPT 页目前不依赖登录态）。
 */
@RestController
@RequestMapping("/ppt/speech")
public class PptSpeechController {

    @Resource
    private PptSpeechService pptSpeechService;
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
     * AI 现场讲词（讲课工作项 ai 模式逐页调用）。
     *
     * <p>无状态：一次请求 = 一页讲词。生成失败抛 {@code PPT_SPEECH_NARRATE_FAILED}，
     * 前端收到后走「重试 / 跳过本页」的 Attention 流程（失败恢复是编排层的事，后端不存会话）。
     */
    @PostMapping("/narrate")
    public CommonResult<PptSpeechNarrateRespVO> narrate(@RequestBody PptSpeechNarrateReqVO reqVO) {
        return success(pptSpeechService.narrate(reqVO));
    }

    /**
     * 流式播报一段讲稿（老师点「开始演讲」或放映页的「试听本页」时调用）。
     *
     * <p>返回的是事件流而不是 {@code CommonResult}：{@code status}（started / done）、若干 {@code chunk}
     * （base64 的 48kHz 单声道 16bit PCM）、失败时的 {@code error}。调用方断开即取消合成。
     * 若在事件流建立前就校验失败（未启用 / 文本为空或过长 / 会议凭据无效），返回的是普通 JSON 错误体——
     * 前端读取器按 content-type 分辨这两种情况。
     *
     * @param reqVO 文本与（可空的）会议信息
     * @return SSE 事件流
     */
    @PostMapping(value = "/speak", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter speak(@RequestBody DigitalSpeakReqVO reqVO) {
        return digitalHumanService.speak(reqVO);
    }

}
