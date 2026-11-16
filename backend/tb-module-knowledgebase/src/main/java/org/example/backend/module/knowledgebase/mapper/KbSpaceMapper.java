package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.model.vo.PublicSpaceRespVO;
import org.example.backend.module.knowledgebase.model.vo.SpaceRespVO;

import java.util.List;

@Mapper
public interface KbSpaceMapper {

    /**
     * 查询空间列表（附带各空间已完成向量化（status = 2）的文档数量），只返回指定创建者的空间
     *
     * <p>注意：kb_document 已改用 deleted_at 表示软删，kb_space 仍为 deleted 布尔列，两者口径不同。
     */
    @Select("SELECT s.id, s.name, s.description, s.visibility, s.create_time, "
            + "COALESCE(d.document_count, 0) AS document_count "
            + "FROM kb_space s "
            + "LEFT JOIN (SELECT space_id, COUNT(*) AS document_count FROM kb_document "
            + "           WHERE status = 2 AND deleted_at IS NULL GROUP BY space_id) d ON d.space_id = s.id "
            + "WHERE s.deleted = FALSE AND s.creator = #{creator} "
            + "ORDER BY s.id DESC")
    List<SpaceRespVO> selectRespList(Long creator);

    /**
     * 查询他人的公有空间列表（「公有库」浏览区）：visibility=1 且创建者不是当前登录用户。
     *
     * <p>创建者显示名取 system_user.nickname，缺失降级 username；创建者为 NULL 的存量空间
     * 不满足 {@code s.creator <> #{userId}}，不出现在公有区（无从归属校验，宁可少展示）。
     */
    @Select("SELECT s.id, s.name, s.description, s.create_time, "
            + "COALESCE(d.document_count, 0) AS document_count, "
            + "COALESCE(u.nickname, u.username, '未知用户') AS creator_name "
            + "FROM kb_space s "
            + "LEFT JOIN (SELECT space_id, COUNT(*) AS document_count FROM kb_document "
            + "           WHERE status = 2 AND deleted_at IS NULL GROUP BY space_id) d ON d.space_id = s.id "
            + "LEFT JOIN system_user u ON u.id = s.creator "
            + "WHERE s.deleted = FALSE AND s.visibility = 1 AND s.creator <> #{userId} "
            + "ORDER BY s.id DESC")
    List<PublicSpaceRespVO> selectPublicList(Long userId);

    @Select("SELECT * FROM kb_space WHERE id = #{id} AND deleted = FALSE")
    KbSpaceDO selectById(Long id);

    /** 名称占用检查（fork 副本命名防撞唯一索引），返回已占用空间的 id，未占用返回 null */
    @Select("SELECT id FROM kb_space WHERE name = #{name} AND deleted = FALSE LIMIT 1")
    Long selectIdByName(String name);

    /** 统计指定空间下已完成向量化（status = 2）的文档数量，与文档列表口径保持一致 */
    @Select("SELECT COUNT(*) FROM kb_document WHERE space_id = #{spaceId} AND status = 2 AND deleted_at IS NULL")
    Long selectDocumentCount(Long spaceId);

    @Insert("INSERT INTO kb_space (name, description, visibility, creator) "
            + "VALUES (#{name}, #{description}, #{visibility}, #{creator})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbSpaceDO space);

    @Update("UPDATE kb_space SET name = #{name}, description = #{description}, update_time = now() "
            + "WHERE id = #{id} AND deleted = FALSE")
    int updateById(KbSpaceDO space);

    @Update("UPDATE kb_space SET visibility = #{visibility}, update_time = now() "
            + "WHERE id = #{id} AND deleted = FALSE")
    int updateVisibility(@Param("id") Long id, @Param("visibility") Integer visibility);

    @Update("UPDATE kb_space SET deleted = TRUE, update_time = now() WHERE id = #{id} AND deleted = FALSE")
    int deleteById(Long id);

}
