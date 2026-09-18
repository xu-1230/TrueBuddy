package org.example.backend.module.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.test.model.RoleDO;

import java.util.List;

@Mapper
public interface RoleMapper {

    @Select("SELECT * FROM \"system_role\" WHERE id = #{id}")
    RoleDO selectById(Long id);

    @Select("SELECT r.code FROM \"system_role\" r "
            + "JOIN \"system_user_role\" ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(Long userId);

}