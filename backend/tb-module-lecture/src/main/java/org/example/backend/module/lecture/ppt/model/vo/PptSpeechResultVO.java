package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * 单页讲稿结果。
 *
 * <p>前两个字段同时是**模型的输出契约**（要求模型返回 {@code [{"index":1,"remark":"…"}]}），
 * {@code chars} / {@code seconds} 由后端按语速常数算好回传，前端直接显示「约 59 秒」。
 */
@Data
public class PptSpeechResultVO {

    /** 页码，从 1 开始（与请求里的 {@code index} 一一对应） */
    private Integer index;

    /** 优化后的讲稿正文（写回前端该页的 remark） */
    private String remark;

    /** 字数（去掉所有空白后统计） */
    private Integer chars;

    /** 预计朗读秒数（字数 ÷ 语速常数） */
    private Integer seconds;

}
