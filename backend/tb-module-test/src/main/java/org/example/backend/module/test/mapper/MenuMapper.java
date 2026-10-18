package org.example.backend.module.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单权限查询（{@code system_menu} / {@code system_role_menu}）。
 */
@Mapper
public interface MenuMapper {

    /**
     * 查询某用户实际生效的权限码。
     *
     * <p>三处过滤缺一不可：用户-角色关联、角色启用状态、菜单启用状态。
     * 权限码为空（目录节点）不返回；{@code DISTINCT} 保证多角色重复授权只出现一次。
     */
    @Select("SELECT DISTINCT m.permission FROM \"system_menu\" m "
            + "JOIN \"system_role_menu\" rm ON rm.menu_id = m.id "
            + "JOIN \"system_user_role\" ur ON ur.role_id = rm.role_id "
            + "JOIN \"system_role\" r ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "AND r.status = 0 "
            + "AND m.status = 0 "
            + "AND m.permission IS NOT NULL "
            + "AND m.permission <> '' "
            + "ORDER BY m.permission")
    List<String> selectPermissionsByUserId(Long userId);

}
