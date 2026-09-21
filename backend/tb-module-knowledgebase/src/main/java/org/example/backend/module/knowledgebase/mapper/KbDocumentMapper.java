package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    @Select("SELECT * FROM kb_document WHERE space_id = #{spaceId} AND deleted_at IS NULL ORDER BY id DESC")
    List<KbDocumentDO> selectListBySpaceId(Long spaceId);

    /**
     * 查询指定空间下「已完成向量化」（status = 2）的文档列表，供文档列表展示。
     *
     * <p>解析中 / 待确认 / 失败等中间态文档不对外展示，故与 {@link #selectListBySpaceId(Long)} 区分开，
     * 避免影响空间删除等需要遍历全部文档的场景。
     */
    @Select("SELECT * FROM kb_document WHERE space_id = #{spaceId} AND status = 2 AND deleted_at IS NULL ORDER BY id DESC")
    List<KbDocumentDO> selectCompletedListBySpaceId(Long spaceId);

    @Select("SELECT * FROM kb_document WHERE id = #{id} AND deleted_at IS NULL")
    KbDocumentDO selectById(Long id);

    @Insert("INSERT INTO kb_document (space_id, file_id, name, type, chunk_count, status, "
            + "docmind_job_id, parse_progress, parse_error) "
            + "VALUES (#{spaceId}, #{fileId}, #{name}, #{type}, #{chunkCount}, #{status}, "
            + "#{docmindJobId}, #{parseProgress}, #{parseError})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbDocumentDO document);

    /** 更新解析状态（解析中 / 失败） */
    @Update("UPDATE kb_document SET status = #{status}, parse_progress = #{progress}, "
            + "parse_error = #{error}, update_time = now() "
            + "WHERE id = #{id} AND deleted_at IS NULL")
    int updateParseState(@Param("id") Long id,
                         @Param("status") Integer status,
                         @Param("progress") Integer progress,
                         @Param("error") String error);

    /** 解析成功：置为「待确认」（4），回写分片数；向量化成功后才由 {@link #updateChunkCount} 置为「已完成」（2） */
    @Update("UPDATE kb_document SET status = 4, chunk_count = #{chunkCount}, "
            + "parse_progress = 100, parse_error = NULL, update_time = now() "
            + "WHERE id = #{id} AND deleted_at IS NULL")
    int updateParseSuccess(@Param("id") Long id,
                           @Param("chunkCount") Integer chunkCount);

    /** 向量化完成：更新分片数与状态 */
    @Update("UPDATE kb_document SET status = 2, chunk_count = #{chunkCount}, update_time = now() "
            + "WHERE id = #{id} AND deleted_at IS NULL")
    int updateChunkCount(@Param("id") Long id, @Param("chunkCount") Integer chunkCount);

    @Update("UPDATE kb_document SET deleted_at = now(), update_time = now() "
            + "WHERE id = #{id} AND deleted_at IS NULL")
    int deleteById(Long id);

    @Update("UPDATE kb_document SET deleted_at = now(), update_time = now() "
            + "WHERE space_id = #{spaceId} AND deleted_at IS NULL")
    int deleteBySpaceId(Long spaceId);

}
