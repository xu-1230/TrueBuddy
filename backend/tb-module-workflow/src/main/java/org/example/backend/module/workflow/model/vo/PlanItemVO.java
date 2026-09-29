package org.example.backend.module.workflow.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批改清单条目（规划阶段产出，教师确认阶段可调整）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanItemVO {

    /** 作业文件相对路径 */
    private String filePath;

    /** 作业文件名 */
    private String fileName;

    /** 是否需要批改（非文本类文件为 false） */
    private Boolean needGrading;

    /** 判定理由 */
    private String reason;

}
