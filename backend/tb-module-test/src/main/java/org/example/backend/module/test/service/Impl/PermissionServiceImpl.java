package org.example.backend.module.test.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.module.test.mapper.MenuMapper;
import org.example.backend.module.test.service.PermissionService;
import org.example.backend.module.test.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * RBAC 解析实现：角色来自 {@code RoleService}，权限来自菜单表。
 *
 * <p>不做缓存：角色或权限调整后，下一次请求立即生效；当前数据量很小，
 * 两条按索引命中的查询比引入失效逻辑更可控。
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    @Resource
    private RoleService roleService;

    @Resource
    private MenuMapper menuMapper;

    @Override
    public List<String> getRoleCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return normalize(roleService.getRoleCodes(userId));
    }

    @Override
    public List<String> getPermissions(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return normalize(menuMapper.selectPermissionsByUserId(userId));
    }

    /** 去空、去重，并保持查询返回的稳定顺序 */
    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            distinct.add(value.trim());
        }
        return distinct.isEmpty() ? List.of() : new ArrayList<>(distinct);
    }

}
