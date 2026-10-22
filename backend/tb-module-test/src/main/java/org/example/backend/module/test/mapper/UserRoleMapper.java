package org.example.backend.module.test.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户-角色关联写入。
 */
@Mapper
public interface UserRoleMapper {

    /**
     * 按角色编码绑定用户；角色不存在或已禁用时返回 0，由调用方回滚。
     */
    @Insert("insert into system_user_role (user_id, role_id) "
            + "select #{userId}, id from system_role where code = #{roleCode} and status = 0")
    int insertByUserIdAndRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
