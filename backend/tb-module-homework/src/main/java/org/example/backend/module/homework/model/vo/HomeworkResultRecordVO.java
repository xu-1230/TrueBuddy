package org.example.backend.module.homework.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

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
    /** 自定义导出列的值：key → 文本/数字（含静态列默认值） */
    private Map<String, Object> custom;
    /** 模型实际读取的文件相对路径（字符串数组） */
    private List<String> readFiles;
    /** @deprecated 旧评语列，已迁移到 custom，仅为旧客户端保留 */
    @Deprecated
    private String comment;
    /** @deprecated 旧扣分明细列，已迁移到 custom，仅为旧客户端保留 */
    @Deprecated
    private List<String> deductions;

}
