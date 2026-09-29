package org.example.backend.module.workflow.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批改三维度得分（各维度 0-100）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradingDimensionsVO {

    /** 准确性 */
    private Double accuracy;

    /** 完整性 */
    private Double completeness;

    /** 表达 */
    private Double expression;

}
