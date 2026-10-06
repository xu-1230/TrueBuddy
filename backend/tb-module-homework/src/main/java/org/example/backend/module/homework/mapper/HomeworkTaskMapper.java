package org.example.backend.module.homework.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
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

}
