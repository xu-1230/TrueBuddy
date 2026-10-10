package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * 试听请求（放映形态「自己先听一遍」，见 {@code docs/18} §5.3）。
 *
 * <p>刻意只带文本、不带会议号：试听不进房间、不落库，与正式播报是两条通路。
 */
@Data
public class PptSpeechSpeakReqVO {

    /** 要合成的文本，通常是本页 {@code remark} 或其一个片段 */
    private String text;

}
