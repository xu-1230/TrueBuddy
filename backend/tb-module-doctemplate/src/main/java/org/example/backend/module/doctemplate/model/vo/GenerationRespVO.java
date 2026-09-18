package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 文档模板生成响应 VO（任务信息 + 文件列表）
 */
@Data
public class GenerationRespVO {

    /** 生成任务 id */
    private Long generationId;
    /** 任务状态：1=成功，2=部分成功，3=失败 */
    private Integer status;
    /** 任务耗时（毫秒） */
    private Long durationMs;
    /** 失败/部分失败的错误摘要（成功时为空） */
    private String errorMsg;
    /** 生成文件列表 */
    private List<DocFileRespVO> docs;

}
