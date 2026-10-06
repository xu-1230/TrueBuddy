package org.example.backend.module.homework.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.homework.model.HomeworkFileDO;

import java.util.List;

@Mapper
public interface HomeworkFileMapper {

    /** 按任务批量写入作业文件（单次上传一次性落库，避免逐条往返） */
    @Insert("<script>"
            + "INSERT INTO homework_file (task_id, file_id, relative_path, name, size) VALUES "
            + "<foreach collection='files' item='f' separator=','>"
            + "(#{f.taskId}, #{f.fileId}, #{f.relativePath}, #{f.name}, #{f.size})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("files") List<HomeworkFileDO> files);

    /** 按任务查询作业文件（按相对路径升序，即目录树阅读顺序） */
    @Select("SELECT * FROM homework_file WHERE task_id = #{taskId} ORDER BY relative_path ASC")
    List<HomeworkFileDO> selectListByTaskId(Long taskId);

}
