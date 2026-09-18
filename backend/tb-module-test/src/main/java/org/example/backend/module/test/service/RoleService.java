package org.example.backend.module.test.service;

import org.example.backend.module.test.model.RoleDO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RoleService {


    RoleDO getRole(Long id);

    List<String> getRoleCodes(Long userId);



}