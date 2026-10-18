package org.example.backend.module.lecture.ppt.service;

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

}
