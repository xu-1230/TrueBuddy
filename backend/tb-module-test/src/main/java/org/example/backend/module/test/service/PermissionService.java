package org.example.backend.module.test.service;

import java.util.List;

/**
 * 用户 → 角色 / 权限的解析入口。
 *
 * <p>权限码的唯一来源是 {@code system_menu.permission}；角色与菜单都只在启用状态下生效。
 */
public interface PermissionService {

    /**
     * 查询用户拥有的角色编码（只含启用角色）。
     *
     * @param userId 用户编号
     * @return 角色编码集合，无角色时为空数组
     */
    List<String> getRoleCodes(Long userId);

    /**
     * 查询用户实际生效的权限码（角色启用 + 菜单启用，去重）。
     *
     * @param userId 用户编号
     * @return 权限码集合，无权限时为空数组
     */
    List<String> getPermissions(Long userId);

}
