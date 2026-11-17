package org.example.backend.module.homework.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.homework.model.HomeworkGradingResultDO;

import java.util.List;

@Mapper
public interface HomeworkGradingResultMapper {

    /**
     * 覆盖式回写：整批插入。
     * custom / read_files 为 JSON 文本，显式 ::jsonb cast（JDBC 按字符串传参，JSONB 列需显式转换）。
     * 旧 comment / deductions 列不写入，由表默认值兜底（legacy，已迁移到 custom）。
     */
    @Insert("<script>"
            + "INSERT INTO homework_grading_result "
            + "(task_id, student, score, grade, custom, read_files, conversation_id) VALUES "
            + "<foreach collection='records' item='r' separator=','>"
            + "(#{r.taskId}, #{r.student}, #{r.score}, #{r.grade}, "
            + "#{r.custom}::jsonb, #{r.readFiles}::jsonb, #{r.conversationId})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("records") List<HomeworkGradingResultDO> records);

    /** 覆盖式回写第一步：物理删除该任务的旧评分记录 */
    @Delete("DELETE FROM homework_grading_result WHERE task_id = #{taskId}")
    int deleteByTaskId(Long taskId);

    /** 按任务查询评分记录（按 id 升序，即名单顺序） */
    @Select("SELECT * FROM homework_grading_result WHERE task_id = #{taskId} ORDER BY id ASC")
    List<HomeworkGradingResultDO> selectListByTaskId(Long taskId);

}
