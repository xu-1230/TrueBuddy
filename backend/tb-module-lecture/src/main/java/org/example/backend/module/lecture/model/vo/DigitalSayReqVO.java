package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 数字人播报请求（老师侧动作，用 {@code hostToken} 鉴权）。
 */
@Data
public class DigitalSayReqVO {

    /** 会议号 */
    private String meetingNo;

    /** 主持人凭据 */
    private String hostToken;

    /** 要播报的文本 */
    private String text;

    /**
     * 播报标识（可空）。
     *
     * <p>刻意允许<b>客户端生成</b>：{@code say} 是同步语义（返回即代表整段已交给 worker），
     * 若只由后端生成，前端要打断正在播的这段时手里还没有 requestId（响应还没回来）。跟页播报的
     * 「翻下一页先掐断上一页」依赖这个能力，见 {@code docs/18} §5.4。
     *
     * <p>只接受字母数字与连字符、长度 8–64；不合规或为空时后端自己生成一个。
     */
    private String requestId;

}
