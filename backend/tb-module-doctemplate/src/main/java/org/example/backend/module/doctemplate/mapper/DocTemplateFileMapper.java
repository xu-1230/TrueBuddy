package org.example.backend.module.doctemplate.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.doctemplate.model.DocTemplateFileDO;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DocTemplateFileMapper {

    @Insert("INSERT INTO doc_template_file "
            + "(generation_id, file_id, name, type, size, tag, summary, sort) "
            + "VALUES (#{generationId}, #{fileId}, #{name}, #{type}, #{size}, #{tag}, #{summary}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DocTemplateFileDO file);

    @Select("SELECT * FROM doc_template_file WHERE generation_id = #{generationId} AND deleted = FALSE "
            + "ORDER BY sort ASC, id ASC")
    List<DocTemplateFileDO> selectListByGenerationId(Long generationId);

    /**
     * 批量取多次生成的产出文件：历史接口一次性展开，避免逐任务查询的 N+1。
     * 调用方须保证 {@code generationIds} 非空（空集合会生成非法 SQL）。
     */
    @Select("<script>"
            + "SELECT * FROM doc_template_file WHERE deleted = FALSE AND generation_id IN "
            + "<foreach collection='generationIds' item='generationId' open='(' separator=',' close=')'>"
            + "#{generationId}"
            + "</foreach> "
            + "ORDER BY generation_id ASC, sort ASC, id ASC"
            + "</script>")
    List<DocTemplateFileDO> selectListByGenerationIds(
            @Param("generationIds") Collection<Long> generationIds);

}
