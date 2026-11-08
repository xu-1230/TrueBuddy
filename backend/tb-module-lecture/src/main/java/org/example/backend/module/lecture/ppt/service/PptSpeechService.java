package org.example.backend.module.lecture.ppt.service;

import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechNarrateRespVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeReqVO;
import org.example.backend.module.lecture.ppt.model.vo.PptSpeechOptimizeRespVO;

/**
 * PPT 讲稿生成（AI 备注优化的服务端实现）。
 */
public interface PptSpeechService {

    /**
     * 生成讲稿：请求里的页数决定是「优化本页」还是「优化全部」。
     *
     * <p>内部按 {@code tb.lecture.ppt.batch-size} 分批调用模型，失败的页会补跑一次；
     * 仍未成功的页码放进响应的 {@code failedIndexes}，不整体报错。
     *
     * @param reqVO 页清单与目标时长
     * @return 每页讲稿（含字数与预计秒数）+ 失败页码
     */
    PptSpeechOptimizeRespVO optimize(PptSpeechOptimizeReqVO reqVO);

    /**
     * AI 现场讲词（narrate）：根据单页的「备注（蓝本）+ 标题/正文（事实边界）+ 现场进度」，
     * 生成一段现在就要念出来的口播文本。
     *
     * <p>与 optimize 的分工：optimize 是「改写并写回」（结果成为 remark），
     * narrate 是「念一次就丢」的一次性派生物——口播只进会议台账，不写回 remark。
     * 无状态：一次请求 = 一页讲词；失败的恢复（重试/跳过）由前端工作项编排。
     *
     * @param reqVO 单页信息与现场上下文
     * @return 口播文本（含字数与预计秒数）
     */
    PptSpeechNarrateRespVO narrate(PptSpeechNarrateReqVO reqVO);

}
