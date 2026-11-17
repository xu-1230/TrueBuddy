package org.example.backend.module.doctemplate.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.doctemplate.model.DocTemplateVersionDO;

import java.util.List;

@Mapper
public interface DocTemplateVersionMapper {

    @Insert("INSERT INTO doc_template_version "
            + "(root_file_id, file_id, parent_version_id, revision, edit_summary, edit_ops, creator) "
            + "VALUES (#{rootFileId}, #{fileId}, #{parentVersionId}, #{revision}, #{editSummary}, "
            + "#{editOps}::jsonb, #{creator})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DocTemplateVersionDO version);

    @Select("SELECT * FROM doc_template_version WHERE id = #{id}")
    DocTemplateVersionDO selectById(@Param("id") Long id);

    /** 当前根文件的最大版本号；没有版本行时返回 1（根文件即 v1） */
    @Select("SELECT COALESCE(MAX(revision), 1) FROM doc_template_version WHERE root_file_id = #{rootFileId}")
    Integer selectMaxRevision(@Param("rootFileId") Long rootFileId);

    @Select("SELECT * FROM doc_template_version WHERE root_file_id = #{rootFileId} ORDER BY revision ASC")
    List<DocTemplateVersionDO> selectListByRootFileId(@Param("rootFileId") Long rootFileId);

    /** 目标版本及其全部后代 id（撤回前用于回传失效列表） */
    @Select("WITH RECURSIVE subtree AS ("
            + "  SELECT id FROM doc_template_version "
            + "   WHERE root_file_id = #{rootFileId} AND id = #{targetVersionId} "
            + "  UNION ALL "
            + "  SELECT v.id FROM doc_template_version v "
            + "   JOIN subtree s ON v.parent_version_id = s.id "
            + "   WHERE v.root_file_id = #{rootFileId}"
            + ") "
            + "SELECT id FROM subtree")
    List<Long> selectSubtreeIds(@Param("rootFileId") Long rootFileId,
                                @Param("targetVersionId") Long targetVersionId);

    /**
     * 撤回：把目标版本及其所有后代置 superseded。
     * 调用方必须先校验目标属于该 root，且在当前版本链上。
     */
    @Update("WITH RECURSIVE subtree AS ("
            + "  SELECT id FROM doc_template_version "
            + "   WHERE root_file_id = #{rootFileId} AND id = #{targetVersionId} "
            + "  UNION ALL "
            + "  SELECT v.id FROM doc_template_version v "
            + "   JOIN subtree s ON v.parent_version_id = s.id "
            + "   WHERE v.root_file_id = #{rootFileId}"
            + ") "
            + "UPDATE doc_template_version SET superseded = TRUE "
            + "WHERE id IN (SELECT id FROM subtree)")
    int supersedeSubtree(@Param("rootFileId") Long rootFileId,
                         @Param("targetVersionId") Long targetVersionId);

}
