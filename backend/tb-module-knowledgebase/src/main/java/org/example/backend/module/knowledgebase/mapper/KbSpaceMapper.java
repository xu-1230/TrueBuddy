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
     * 查询空间列表（附带各空间未删除文档数量）
     */
    @Select("SELECT s.id, s.name, s.description, s.create_time, "
            + "COALESCE(d.document_count, 0) AS document_count "
            + "FROM kb_space s "
            + "LEFT JOIN (SELECT space_id, COUNT(*) AS document_count FROM kb_document "
            + "           WHERE deleted = FALSE GROUP BY space_id) d ON d.space_id = s.id "
            + "WHERE s.deleted = FALSE "
            + "ORDER BY s.id DESC")
    List<SpaceRespVO> selectRespList();

    @Select("SELECT * FROM kb_space WHERE id = #{id} AND deleted = FALSE")
    KbSpaceDO selectById(Long id);

    @Select("SELECT COUNT(*) FROM kb_document WHERE space_id = #{spaceId} AND deleted = FALSE")
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
