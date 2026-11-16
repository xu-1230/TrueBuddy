package org.example.backend.module.homework.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 逐学生评分记录 VO（回写请求的 records 元素 + 任务详情回看共用）
 */
@Data
public class HomeworkResultRecordVO {

    /** 学生姓名（模型解析；空值由服务端填「未署名」） */
    private String student;
    /** 得分（可空） */
    private Double score;
    /** 等级（优 / 良 / 及格…） */
    private String grade;
    /** 评语 */
    private String comment;
    /** 扣分明细（字符串数组） */
    private List<String> deductions;
    /** 模型实际读取的文件相对路径（字符串数组） */
    private List<String> readFiles;

}
