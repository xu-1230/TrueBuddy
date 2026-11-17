package org.example.backend.module.lecture.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.lecture.model.AvatarModelDO;

import java.util.List;

@Mapper
public interface AvatarModelMapper {

    /** 启用中的形象目录，按 sort、id 升序 */
    @Select("SELECT * FROM avatar_model WHERE deleted = FALSE AND enabled = TRUE ORDER BY sort, id")
    List<AvatarModelDO> selectEnabled();

    /** 按编码取启用中的形象（用于偏好保存校验） */
    @Select("SELECT * FROM avatar_model WHERE model_code = #{modelCode} AND deleted = FALSE AND enabled = TRUE")
    AvatarModelDO selectEnabledByCode(@Param("modelCode") String modelCode);

}
