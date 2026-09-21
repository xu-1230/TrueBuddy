package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.knowledgebase.model.KbDocumentChunkDO;

import java.util.List;

@Mapper
public interface KbDocumentChunkMapper {

    @Select("SELECT * FROM kb_document_chunk WHERE document_id = #{documentId} ORDER BY chunk_index ASC")
    List<KbDocumentChunkDO> selectListByDocumentId(Long documentId);

    @Insert("INSERT INTO kb_document_chunk (document_id, space_id, chunk_index, content) "
            + "VALUES (#{documentId}, #{spaceId}, #{chunkIndex}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbDocumentChunkDO chunk);

    /** 从属表采用物理删除：重解析时先清空旧分片再重建，避免软删堆积 */
    @Delete("DELETE FROM kb_document_chunk WHERE document_id = #{documentId}")
    int deleteByDocumentId(Long documentId);

}
