package org.example.backend.module.test.service.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.util.object.BeanUtils;
import org.example.backend.module.test.mapper.AdminUserMapper;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.UserSaveReqVO;
import org.example.backend.module.test.service.AdminUserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test.enums.ErrorCodeConstants.*;

@Service
public class AdminUserServiceImpl implements AdminUserService {


    @Resource
    private AdminUserMapper userMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveReqVO createReqVO) {


        // 1.2 校验正确性
        validateUserForCreateOrUpdate(null, createReqVO.getUsername(), createReqVO.getEmail());
        // 2.1 插入用户
        AdminUserDO user = BeanUtils.toBean(createReqVO, AdminUserDO.class);
        user.setStatus(0); // 默认开启
        user.setPassword(createReqVO.getPassword()); // 加密密码
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public AdminUserDO getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public AdminUserDO getUser(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return true;
    }

    private AdminUserDO validateUserForCreateOrUpdate(Long id, String username, String email) {
        // 校验用户存在
        AdminUserDO user = validateUserExists(id);
        // 校验用户名唯一
        validateUsernameUnique(id, username);
        // 校验邮箱唯一
        validateEmailUnique(id, email);

        return user;
    }

    @VisibleForTesting
    AdminUserDO validateUserExists(Long id) {
        if (id == null) {
            return null;
        }
        AdminUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    @VisibleForTesting
    void validateUsernameUnique(Long id, String username) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        AdminUserDO user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_USERNAME_EXISTS);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_USERNAME_EXISTS);
        }
    }

    @VisibleForTesting
    void validateEmailUnique(Long id, String email) {
        if (StrUtil.isBlank(email)) {
            return;
        }
        AdminUserDO user = userMapper.selectByEmail(email);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_EMAIL_EXISTS);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_EMAIL_EXISTS);
        }
    }



}
