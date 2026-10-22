package org.example.backend.module.test.service;

import cn.hutool.core.collection.CollUtil;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.UserSaveReqVO;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AdminUserService {

    Long createUser(UserSaveReqVO createReqVO);

    AdminUserDO getUserByUsername(String username);

    AdminUserDO getUserByEmail(String email);

    AdminUserDO getUser(Long id);

    boolean isPasswordMatch(String rawPassword, String encodedPassword);

    /**
     * 重置用户密码（入参为明文，内部做 MD5）。
     */
    void updatePassword(Long id, String rawPassword);
}