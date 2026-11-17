package org.example.backend.module.doctemplate.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.doctemplate.model.DocTemplateChatMessageDO;

import java.util.List;

@Mapper
public interface DocTemplateChatMessageMapper {

    @Insert("INSERT INTO doc_template_chat_message "
            + "(root_file_id, role, content, blocks, selections, version_id, superseded, error, creator, create_time) "
            + "VALUES (#{rootFileId}, #{role}, #{content}, "
            + "#{blocks,jdbcType=OTHER}::jsonb, #{selections,jdbcType=OTHER}::jsonb, "
            + "#{versionId}, #{superseded}, #{error}, #{creator}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DocTemplateChatMessageDO message);

    /** 某工作区最近 N 条消息（内层倒序取最近，外层正序返回） */
    @Select("SELECT * FROM (SELECT * FROM doc_template_chat_message "
            + "WHERE root_file_id = #{rootFileId} ORDER BY id DESC LIMIT #{limit}) t ORDER BY t.id ASC")
    List<DocTemplateChatMessageDO> selectRecentByRootFileId(@Param("rootFileId") Long rootFileId,
                                                            @Param("limit") int limit);

    /** LLM 上下文：最近 N 条有效的 user / assistant 消息（排除失效与失败） */
    @Select("SELECT * FROM (SELECT * FROM doc_template_chat_message "
            + "WHERE root_file_id = #{rootFileId} AND role IN ('user','assistant') "
            + "AND superseded = FALSE AND error = FALSE "
            + "ORDER BY id DESC LIMIT #{limit}) t ORDER BY t.id ASC")
    List<DocTemplateChatMessageDO> selectRecentValidByRootFileId(@Param("rootFileId") Long rootFileId,
                                                                 @Param("limit") int limit);

    /** 撤回目标版本对应的助手消息 */
    @Select("SELECT * FROM doc_template_chat_message "
            + "WHERE root_file_id = #{rootFileId} AND version_id = #{versionId} "
            + "AND role = 'assistant' ORDER BY id ASC LIMIT 1")
    DocTemplateChatMessageDO selectFirstByVersionId(@Param("rootFileId") Long rootFileId,
                                                    @Param("versionId") Long versionId);

    /** 旧版本没有消息行时的兜底：被丢弃版本里最早的一条消息 id */
    @Select("<script>SELECT MIN(id) FROM doc_template_chat_message "
            + "WHERE root_file_id = #{rootFileId} AND version_id IN "
            + "<foreach collection='versionIds' item='versionId' open='(' separator=',' close=')'>"
            + "#{versionId}</foreach></script>")
    Long selectMinIdByVersionIds(@Param("rootFileId") Long rootFileId,
                                 @Param("versionIds") List<Long> versionIds);

    /** 撤回：目标消息及其之后的全部消息置失效 */
    @Update("UPDATE doc_template_chat_message SET superseded = TRUE "
            + "WHERE root_file_id = #{rootFileId} AND id >= #{fromId} AND superseded = FALSE")
    int supersedeFromId(@Param("rootFileId") Long rootFileId, @Param("fromId") Long fromId);

    /** 目标消息之后仍有效的编辑消息数（撤回提示用） */
    @Select("SELECT COUNT(*) FROM doc_template_chat_message "
            + "WHERE root_file_id = #{rootFileId} AND id > #{afterId} "
            + "AND role = 'assistant' AND version_id IS NOT NULL "
            + "AND error = FALSE AND superseded = FALSE")
    int countValidEditsAfter(@Param("rootFileId") Long rootFileId, @Param("afterId") Long afterId);

}
