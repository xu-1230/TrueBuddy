package org.example.backend.module.doctemplate.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.doctemplate.model.DocTemplateFileDO;

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

}
