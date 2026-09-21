package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.model.vo.SpaceRespVO;

import java.util.List;

@Mapper
public interface KbSpaceMapper {

    /**
     * 查询空间列表（附带各空间已完成向量化（status = 2）的文档数量）
     *
     * <p>注意：kb_document 已改用 deleted_at 表示软删，kb_space 仍为 deleted 布尔列，两者口径不同。
     */
    @Select("SELECT s.id, s.name, s.description, s.create_time, "
            + "COALESCE(d.document_count, 0) AS document_count "
            + "FROM kb_space s "
            + "LEFT JOIN (SELECT space_id, COUNT(*) AS document_count FROM kb_document "
            + "           WHERE status = 2 AND deleted_at IS NULL GROUP BY space_id) d ON d.space_id = s.id "
            + "WHERE s.deleted = FALSE "
            + "ORDER BY s.id DESC")
    List<SpaceRespVO> selectRespList();

    @Select("SELECT * FROM kb_space WHERE id = #{id} AND deleted = FALSE")
    KbSpaceDO selectById(Long id);

    /** 统计指定空间下已完成向量化（status = 2）的文档数量，与文档列表口径保持一致 */
    @Select("SELECT COUNT(*) FROM kb_document WHERE space_id = #{spaceId} AND status = 2 AND deleted_at IS NULL")
    Long selectDocumentCount(Long spaceId);

    @Insert("INSERT INTO kb_space (name, description) VALUES (#{name}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbSpaceDO space);

    @Update("UPDATE kb_space SET name = #{name}, description = #{description}, update_time = now() "
            + "WHERE id = #{id} AND deleted = FALSE")
    int updateById(KbSpaceDO space);

    @Update("UPDATE kb_space SET deleted = TRUE, update_time = now() WHERE id = #{id} AND deleted = FALSE")
    int deleteById(Long id);

}
