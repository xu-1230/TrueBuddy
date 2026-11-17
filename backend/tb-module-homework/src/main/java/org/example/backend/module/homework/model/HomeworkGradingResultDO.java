package org.example.backend.module.homework.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批改评分结果 DO（表 homework_grading_result）
 * <p>一次批改的逐学生评分记录；同一任务重复回写时先物理删除旧行再整批插入（覆盖式）。
 * deductions / readFiles 在库里是 JSONB，这里以 JSON 文本承载，出入参在 VO 边界互转。
 */
@Data
public class HomeworkGradingResultDO {

    /** 主键（自增） */
    private Long id;
    /** 所属任务 id（homework_task.id） */
    private Long taskId;
    /** 学生姓名（模型解析；空值由服务端填「未署名」） */
    private String student;
    /** 得分（可空） */
    private BigDecimal score;
    /** 等级（优 / 良 / 及格…，模型产出） */
    private String grade;
    /** LEGACY：旧评语列（已迁移到 custom，停止写入） */
    private String comment;
    /** LEGACY：旧扣分明细列（JSONB，JSON 数组文本；已迁移到 custom，停止写入） */
    private String deductions;
    /** 自定义导出列的值（JSONB，JSON 对象文本） */
    private String custom;
    /** 模型实际读取的文件相对路径（JSONB，JSON 数组文本） */
    private String readFiles;
    /** 产出本批结果的工作流会话 id（溯源） */
    private String conversationId;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间（覆盖式回写时刷新） */
    private LocalDateTime updateTime;

}
