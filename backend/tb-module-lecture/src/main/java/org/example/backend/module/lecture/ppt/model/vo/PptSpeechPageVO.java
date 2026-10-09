package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * 请求体里的一页 PPT（字段与 test2 的 {@code PptxPage} 对齐：{@code index} / {@code text} / {@code remark}）。
 *
 * <p>正文与备注都由前端提取后送上来：前端的 PPT 数据来自本地导入（pptx-to-pptist），
 * 服务端不持有这份文档，因此后端不做 pptx 解析（无状态接口）。
 */
@Data
public class PptSpeechPageVO {

    /** 页码，从 1 开始（前端按幻灯片顺序生成，回填时靠它对位） */
    private Integer index;

    /** 该页标题（前端取正文首行，可为空） */
    private String title;

    /** 该页正文纯文本（可空） */
    private String text;

    /** 该页现有备注（可空）—— AI 的重写对象 */
    private String remark;

}
