package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

/**
 * 生成历史记录 VO（对应前端历史记录抽屉）
 */
@Data
public class GenerationHistoryRespVO {

    /** 生成任务 id */
    private Long id;
    /** 标题（描述摘要） */
    private String title;
    /** 知识库名称（未启用检索时为「默认知识库」） */
    private String kb;
    /** 类型（固定「模板生成」） */
    private String type;
    /** 生成时间字符串（yyyy-MM-dd HH:mm） */
    private String time;
    /** 状态文案：成功 / 部分成功 / 失败 */
    private String status;
    /** 产出文档数量 */
    private Integer docCount;

}
