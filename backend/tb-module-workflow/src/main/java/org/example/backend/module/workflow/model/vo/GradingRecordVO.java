package org.example.backend.module.workflow.model.vo;

import lombok.Data;

/**
 * 逐份批改记录（接口返回，字段对齐前端 {@code api/homework.js} 的 SubmissionVO）。
 */
@Data
public class GradingRecordVO {

    /** 作业文件名 */
    private String fileName;

    /** 作业文件相对路径 */
    private String filePath;

    /** 得分（满分由入参 fullScore 决定；解析失败时为 null） */
    private Integer totalScore;

    /** 等级：优秀 / 良好 / 及格 / 待改进 */
    private String grade;

    /** 三维度得分 */
    private GradingDimensionsVO dimensions;

    /** 教师评语 */
    private String comment;

    /**
     * 按得分占比计算等级（与前端作业批改页面口径一致）。
     *
     * @param score    得分，允许为 null（表示未成功批改）
     * @param fullScore 满分
     */
    public static String resolveGrade(Integer score, Integer fullScore) {
        if (score == null) {
            return "待改进";
        }
        int full = fullScore == null || fullScore <= 0 ? 100 : fullScore;
        double ratio = score * 100.0 / full;
        if (ratio >= 90) {
            return "优秀";
        }
        if (ratio >= 75) {
            return "良好";
        }
        if (ratio >= 60) {
            return "及格";
        }
        return "待改进";
    }

}
