package org.example.backend.module.test.service.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.pojo.PageResult;
import org.example.backend.framework.common.util.crypto.MD5Utils;
import org.example.backend.framework.common.util.object.BeanUtils;
import org.example.backend.module.test.mapper.AdminUserMapper;
import org.example.backend.module.test.mapper.UserRoleMapper;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.AdminUserPageReqVO;
import org.example.backend.module.test.model.vo.AdminUserRespVO;
import org.example.backend.module.test.model.vo.UserSaveReqVO;
import org.example.backend.module.test.service.AdminUserService;
import org.example.backend.module.test.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test.enums.ErrorCodeConstants.*;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    /** 分页上限：防止 size 传大值拖垮查询 */
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private AdminUserMapper userMapper;

    @Resource
    private UserRoleMapper userRoleMapper;

    @Resource
    private RoleService roleService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveReqVO createReqVO) {


        // 1.2 校验正确性
        validateUserForCreateOrUpdate(null, createReqVO.getUsername(), createReqVO.getEmail());
        // 2.1 插入用户
        AdminUserDO user = BeanUtils.toBean(createReqVO, AdminUserDO.class);
        // 状态：管理端可指定，注册路径不传默认开启
        user.setStatus(createReqVO.getStatus() != null ? createReqVO.getStatus() : 0);
        user.setPassword(MD5Utils.encrypt(createReqVO.getPassword()));
        userMapper.insert(user);
        // 2.2 角色绑定：注册路径 roles 为空不绑定（由调用方绑定默认角色），管理端创建按传入绑定
        bindRoles(user.getId(), createReqVO.getRoles());
        return user.getId();
    }

    @Override
    public PageResult<AdminUserRespVO> getUserPage(AdminUserPageReqVO pageReqVO) {
        int page = pageReqVO.getPage() != null && pageReqVO.getPage() > 0 ? pageReqVO.getPage() : 1;
        int size = pageReqVO.getSize() != null && pageReqVO.getSize() > 0
                ? Math.min(pageReqVO.getSize(), MAX_PAGE_SIZE) : 10;
        String keyword = StrUtil.trimToNull(pageReqVO.getKeyword());
        String role = StrUtil.trimToNull(pageReqVO.getRole());

        List<AdminUserDO> users = userMapper.selectPage(
                keyword, pageReqVO.getStatus(), role, (page - 1) * size, size);
        long total = userMapper.selectCount(keyword, pageReqVO.getStatus(), role);

        List<AdminUserRespVO> list = new ArrayList<>(users.size());
        for (AdminUserDO user : users) {
            AdminUserRespVO respVO = BeanUtils.toBean(user, AdminUserRespVO.class);
            respVO.setRoles(roleService.getRoleCodes(user.getId()));
            list.add(respVO);
        }
        return new PageResult<>(list, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserSaveReqVO reqVO) {
        // 编辑不改账号：username 传 null 跳过唯一性校验，昵称/邮箱/状态按传值更新
        String nickname = StrUtil.trimToNull(reqVO.getNickname());
        String email = StrUtil.trimToNull(reqVO.getEmail());
        validateUserForCreateOrUpdate(reqVO.getId(), null, email);

        if (nickname != null || email != null || reqVO.getStatus() != null) {
            userMapper.updateById(reqVO.getId(), nickname, email, reqVO.getStatus());
        }

        // 角色传值即全量重绑：先清空再逐个绑定，与用户更新同事务
        if (CollUtil.isNotEmpty(reqVO.getRoles())) {
            userRoleMapper.deleteByUserId(reqVO.getId());
            bindRoles(reqVO.getId(), reqVO.getRoles());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        // 物理删除：先清角色绑定再删用户，防止残留孤儿绑定
        validateUserExists(id);
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
    }

    /**
     * 逐个绑定角色；角色不存在或被禁用时 insert 返回 0，抛错回滚整个事务。
     */
    private void bindRoles(Long userId, Set<String> roleCodes) {
        if (CollUtil.isEmpty(roleCodes)) {
            return;
        }
        for (String roleCode : roleCodes) {
            if (userRoleMapper.insertByUserIdAndRoleCode(userId, roleCode) == 0) {
                throw exception(USER_ROLE_INVALID);
            }
        }
    }

    @Override
    public AdminUserDO getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public AdminUserDO getUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    @Override
    public void updatePassword(Long id, String rawPassword) {
        validateUserExists(id);
        userMapper.updatePassword(id, MD5Utils.encrypt(rawPassword));
    }

    @Override
    public AdminUserDO getUser(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 按 MD5 比对密码；空值一律视为不匹配。
     */
    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        if (StrUtil.isBlank(rawPassword) || StrUtil.isBlank(encodedPassword)) {
            return false;
        }
        return MD5Utils.encrypt(rawPassword).equalsIgnoreCase(encodedPassword);
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
