package org.example.backend.module.homework.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.homework.model.HomeworkTaskDO;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface HomeworkTaskMapper {

    /** 新增任务（自增主键回填 id） */
    @Insert("INSERT INTO homework_task (folder_name, file_count, total_size, status) "
            + "VALUES (#{folderName}, #{fileCount}, #{totalSize}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HomeworkTaskDO task);

    /** 按主键查询（仅未删除） */
    @Select("SELECT * FROM homework_task WHERE id = #{id} AND deleted = FALSE")
    HomeworkTaskDO selectById(Long id);

    /** 写入本次批改的配置（批改要求 + 知识库 + 评分区间 + 自定义导出列），供历史回看与导出使用 */
    @Update("UPDATE homework_task SET description = #{description}, space_ids = #{spaceIds}, "
            + "score_min = #{scoreMin}, score_max = #{scoreMax}, pass_score = #{passScore}, "
            + "custom_fields = #{customFields}::jsonb, "
            + "update_time = now() WHERE id = #{id}")
    int updateGradeConfig(@Param("id") Long id,
                          @Param("description") String description,
                          @Param("spaceIds") String spaceIds,
                          @Param("scoreMin") BigDecimal scoreMin,
                          @Param("scoreMax") BigDecimal scoreMax,
                          @Param("passScore") BigDecimal passScore,
                          @Param("customFields") String customFields);

    /** 最近 N 条任务（历史抽屉列表，口径同文档生成历史：按创建时间倒序；附带评分记录数） */
    @Select("SELECT t.*, (SELECT COUNT(*) FROM homework_grading_result r WHERE r.task_id = t.id) AS result_count "
            + "FROM homework_task t WHERE t.deleted = FALSE "
            + "ORDER BY t.create_time DESC, t.id DESC LIMIT #{limit}")
    List<HomeworkTaskDO> selectRecentList(@Param("limit") int limit);

    /** 开始执行：仅在待批改（0）时置为批改中（1），返回受影响行数供状态机判断 */
    @Update("UPDATE homework_task SET status = 1, update_time = now() "
            + "WHERE id = #{id} AND status = 0")
    int updateStatusToGrading(@Param("id") Long id);

    /** 终态回写：从批改中（1）或既有终态（2/3/4）推进；待批改（0）不可直接写终态 */
    @Update("UPDATE homework_task SET status = #{status}, update_time = now() "
            + "WHERE id = #{id} AND status IN (1, 2, 3, 4)")
    int updateStatusToFinal(@Param("id") Long id, @Param("status") Integer status);

}
