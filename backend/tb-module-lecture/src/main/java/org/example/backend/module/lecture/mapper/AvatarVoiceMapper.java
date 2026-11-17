package org.example.backend.module.lecture.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.lecture.model.AvatarVoiceDO;

import java.util.List;

@Mapper
public interface AvatarVoiceMapper {

    /**
     * 当前用户可见的音色：内置音色（creator 为空）+ 自己复刻的音色。
     * 复刻音色排在前面，其余按分类、id 升序。
     */
    @Select("SELECT * FROM avatar_voice WHERE deleted = FALSE "
            + "AND (creator IS NULL OR creator = #{creator}) "
            + "ORDER BY CASE WHEN creator IS NULL THEN 1 ELSE 0 END, category, id")
    List<AvatarVoiceDO> selectVisible(@Param("creator") Long creator);

    /** 按编码取未删除的音色（播报解析与偏好校验共用） */
    @Select("SELECT * FROM avatar_voice WHERE voice_code = #{voiceCode} AND deleted = FALSE")
    AvatarVoiceDO selectByCode(@Param("voiceCode") String voiceCode);

    /** 按主键取未删除的音色（删除接口用） */
    @Select("SELECT * FROM avatar_voice WHERE id = #{id} AND deleted = FALSE")
    AvatarVoiceDO selectById(@Param("id") Long id);

    /** 同名音色是否已存在（内置音色也算，避免复刻名与内置名混淆） */
    @Select("SELECT count(*) FROM avatar_voice WHERE deleted = FALSE AND name = #{name} "
            + "AND (creator IS NULL OR creator = #{creator})")
    int countByVisibleName(@Param("creator") Long creator, @Param("name") String name);

    /** 新增复刻音色（自增主键回填 id） */
    @Insert("INSERT INTO avatar_voice (voice_code, name, trait, age, language, category, gender, source, "
            + "tts_model, audio_preview_url, sample_file_id, sample_audio_url, creator, updater) "
            + "VALUES (#{voiceCode}, #{name}, #{trait}, #{age}, #{language}, #{category}, #{gender}, #{source}, "
            + "#{ttsModel}, #{audioPreviewUrl}, #{sampleFileId}, #{sampleAudioUrl}, #{creator}, #{updater})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AvatarVoiceDO voice);

    /** 软删自己的复刻音色；返回 0 表示不是自己的或已删除 */
    @Update("UPDATE avatar_voice SET deleted = TRUE, update_time = now(), updater = #{creator} "
            + "WHERE id = #{id} AND creator = #{creator} AND deleted = FALSE")
    int softDeleteOwned(@Param("id") Long id, @Param("creator") Long creator);

}
