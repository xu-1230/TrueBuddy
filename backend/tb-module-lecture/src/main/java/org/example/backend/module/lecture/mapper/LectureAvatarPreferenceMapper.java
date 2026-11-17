package org.example.backend.module.lecture.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.lecture.model.LectureAvatarPreferenceDO;

@Mapper
public interface LectureAvatarPreferenceMapper {

    /** 当前用户未删除的偏好行 */
    @Select("SELECT * FROM lecture_avatar_preference WHERE creator = #{creator} AND deleted = FALSE")
    LectureAvatarPreferenceDO selectByCreator(@Param("creator") Long creator);

    /** 新增偏好（自增主键回填 id） */
    @Insert("INSERT INTO lecture_avatar_preference (creator, avatar_model_code, voice_code, updater) "
            + "VALUES (#{creator}, #{avatarModelCode}, #{voiceCode}, #{updater})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LectureAvatarPreferenceDO preference);

    /** 更新偏好 */
    @Update("UPDATE lecture_avatar_preference SET avatar_model_code = #{avatarModelCode}, "
            + "voice_code = #{voiceCode}, update_time = now(), updater = #{updater} "
            + "WHERE id = #{id} AND deleted = FALSE")
    int update(LectureAvatarPreferenceDO preference);

    /** 音色被删除后，把所有引用它的偏好回退到默认音色 */
    @Update("UPDATE lecture_avatar_preference SET voice_code = NULL, update_time = now() "
            + "WHERE voice_code = #{voiceCode} AND deleted = FALSE")
    int clearVoiceByCode(@Param("voiceCode") String voiceCode);

}
