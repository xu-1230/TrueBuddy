package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.knowledgebase.model.KbDocumentChunkDO;

import java.util.List;

@Mapper
public interface KbDocumentChunkMapper {

    @Select("SELECT * FROM kb_document_chunk WHERE document_id = #{documentId} AND deleted = FALSE "
            + "ORDER BY chunk_index ASC")
    List<KbDocumentChunkDO> selectListByDocumentId(Long documentId);

    @Insert("INSERT INTO kb_document_chunk (document_id, chunk_index, title, content) "
            + "VALUES (#{documentId}, #{chunkIndex}, #{title}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbDocumentChunkDO chunk);

    @Update("UPDATE kb_document_chunk SET deleted = TRUE, update_time = now() "
            + "WHERE document_id = #{documentId} AND deleted = FALSE")
    int deleteByDocumentId(Long documentId);

}
