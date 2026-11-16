package org.example.backend.module.test.service;

import org.example.backend.framework.common.pojo.PageResult;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.AdminUserPageReqVO;
import org.example.backend.module.test.model.vo.AdminUserRespVO;
import org.example.backend.module.test.model.vo.UserSaveReqVO;

import java.util.List;

public interface AdminUserService {

    Long createUser(UserSaveReqVO createReqVO);

    /**
     * 管理端用户分页：keyword（账号/昵称/邮箱模糊）、status、role 筛选。
     */
    PageResult<AdminUserRespVO> getUserPage(AdminUserPageReqVO pageReqVO);

    /**
     * 管理端编辑用户：账号不可改，昵称/邮箱/状态按传值更新；roles 非空时全量重绑。
     */
    void updateUser(UserSaveReqVO reqVO);

    /**
     * 物理删除用户，并同事务清理角色绑定。
     */
    void deleteUser(Long id);

    AdminUserDO getUserByUsername(String username);

    AdminUserDO getUserByEmail(String email);

    AdminUserDO getUser(Long id);

    boolean isPasswordMatch(String rawPassword, String encodedPassword);

    /**
     * 重置用户密码（入参为明文，内部做 MD5）。
     */
    void updatePassword(Long id, String rawPassword);
}