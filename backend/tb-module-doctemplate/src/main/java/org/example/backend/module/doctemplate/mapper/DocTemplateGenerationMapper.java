package org.example.backend.module.doctemplate.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.doctemplate.model.DocTemplateGenerationDO;

import java.util.List;

@Mapper
public interface DocTemplateGenerationMapper {

    @Insert("INSERT INTO doc_template_generation "
            + "(template_file_id, space_id, description, quality, doc_count, status) "
            + "VALUES (#{templateFileId}, #{spaceId}, #{description}, #{quality}, #{docCount}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DocTemplateGenerationDO generation);

    /** 回填任务结果：状态 / 产出数量 / 错误摘要 / 耗时 */
    @Update("UPDATE doc_template_generation SET status = #{status}, doc_count = #{docCount}, "
            + "error_msg = #{errorMsg}, duration_ms = #{durationMs}, update_time = now() "
            + "WHERE id = #{id} AND deleted = FALSE")
    int updateResult(DocTemplateGenerationDO generation);

    @Select("SELECT * FROM doc_template_generation WHERE id = #{id} AND deleted = FALSE")
    DocTemplateGenerationDO selectById(@Param("id") Long id);

    /** 历史记录：按时间倒序取最近 N 条 */
    @Select("SELECT * FROM doc_template_generation WHERE deleted = FALSE ORDER BY id DESC LIMIT #{limit}")
    List<DocTemplateGenerationDO> selectList(@Param("limit") int limit);

}
