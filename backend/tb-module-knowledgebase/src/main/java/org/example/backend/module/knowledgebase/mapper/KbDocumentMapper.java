package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    @Select("SELECT * FROM kb_document WHERE space_id = #{spaceId} AND deleted = FALSE ORDER BY id DESC")
    List<KbDocumentDO> selectListBySpaceId(Long spaceId);

    @Select("SELECT * FROM kb_document WHERE id = #{id} AND deleted = FALSE")
    KbDocumentDO selectById(Long id);

    @Insert("INSERT INTO kb_document (space_id, file_id, name, type, chunk_count, status) "
            + "VALUES (#{spaceId}, #{fileId}, #{name}, #{type}, #{chunkCount}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbDocumentDO document);

    @Update("UPDATE kb_document SET deleted = TRUE, update_time = now() WHERE id = #{id} AND deleted = FALSE")
    int deleteById(Long id);

    @Update("UPDATE kb_document SET deleted = TRUE, update_time = now() WHERE space_id = #{spaceId} AND deleted = FALSE")
    int deleteBySpaceId(Long spaceId);

}
