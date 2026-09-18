package org.example.backend.module.test.service.Impl;


import jakarta.annotation.Resource;
import org.example.backend.module.test.mapper.RoleMapper;
import org.example.backend.module.test.model.RoleDO;
import org.example.backend.module.test.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private RoleMapper roleMapper;

    @Override
    public RoleDO getRole(Long id) {
        return roleMapper.selectById(id);
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

}
