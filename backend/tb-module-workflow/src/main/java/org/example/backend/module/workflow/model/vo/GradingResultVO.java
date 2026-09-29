package org.example.backend.module.workflow.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 单份作业的批改结果（大模型输出的解析结果，内部模型）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GradingResultVO {

    /** 总分（满分由工作流入参 fullScore 决定） */
    private Integer totalScore;

    /** 三维度得分 */
    private GradingDimensionsVO dimensions;

    /** 教师评语 */
    private String comment;

}
