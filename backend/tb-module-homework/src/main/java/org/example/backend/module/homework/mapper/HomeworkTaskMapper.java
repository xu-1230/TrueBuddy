package org.example.backend.module.homework.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.homework.model.HomeworkTaskDO;

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

    /** 写入本次批改的配置（批改要求 + 知识库），供工作流开始节点按 taskId 回查 */
    @Update("UPDATE homework_task SET description = #{description}, space_ids = #{spaceIds}, "
            + "update_time = now() WHERE id = #{id}")
    int updateGradeConfig(@Param("id") Long id,
                          @Param("description") String description,
                          @Param("spaceIds") String spaceIds);

}
