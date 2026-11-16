package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
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

    /**
     * fork 复制：把源文档的分片整批复制到副本文档名下（同事务 INSERT…SELECT）。
     *
     * @return 复制的分片数（供与原库 chunk_count 对账）
     */
    @Insert("INSERT INTO kb_document_chunk (document_id, space_id, chunk_index, content, token_count) "
            + "SELECT #{newDocId}, #{newSpaceId}, chunk_index, content, token_count "
            + "FROM kb_document_chunk WHERE document_id = #{sourceDocId}")
    int copyChunks(@Param("sourceDocId") Long sourceDocId,
                   @Param("newDocId") Long newDocId,
                   @Param("newSpaceId") Long newSpaceId);

    /** 源文档在向量库中的行数（fork 向量对账基准） */
    @Select("SELECT COUNT(*) FROM vector_store WHERE (metadata->>'documentId')::bigint = #{documentId}")
    long countVectorsByDocument(@Param("documentId") Long documentId);

    /**
     * fork 复制向量行：同事务 INSERT…SELECT，生成新 uuid，metadata 改写为副本的
     * spaceId / documentId / chunkId（新旧 chunkId 用 chunk_index 对上）。
     *
     * <p>陈旧向量（metadata 里的 chunkId 指向已消失的分片）join 不上，会被静默丢弃——
     * 由调用方对账「复制数 < 源库数」兜底，宁可回滚也不产出指向错乱分片的副本。
     *
     * @return 复制的向量行数
     */
    @Insert("INSERT INTO vector_store (id, content, metadata, embedding) "
            + "SELECT uuid_generate_v4(), v.content, "
            + "(v.metadata - 'spaceId' - 'documentId' - 'chunkId') "
            + "|| jsonb_build_object('spaceId', CAST(#{newSpaceId} AS text), "
            + "'documentId', CAST(#{newDocId} AS text), 'chunkId', new_chunk.id::text), "
            + "v.embedding "
            + "FROM vector_store v "
            + "JOIN kb_document_chunk src ON src.id = CAST(v.metadata->>'chunkId' AS bigint) "
            + "JOIN kb_document_chunk new_chunk ON new_chunk.document_id = #{newDocId} "
            + "AND new_chunk.chunk_index = src.chunk_index "
            + "WHERE (v.metadata->>'documentId')::bigint = #{sourceDocId}")
    int copyVectors(@Param("sourceDocId") Long sourceDocId,
                    @Param("newDocId") Long newDocId,
                    @Param("newSpaceId") Long newSpaceId);

}
