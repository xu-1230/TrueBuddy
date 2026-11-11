package org.example.backend.module.test.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.framework.common.pojo.PageResult;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.test.model.vo.AdminUserPageReqVO;
import org.example.backend.module.test.model.vo.AdminUserRespVO;
import org.example.backend.module.test.model.vo.UserSaveReqVO;
import org.example.backend.module.test.service.AdminUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test.enums.ErrorCodeConstants.USER_DELETE_SELF;

/**
 * 管理端用户管理接口：分页 / 新增 / 编辑 / 删除。
 *
 * <p>权限码来源 rbac_seed.sql：列表 / 新增 / 编辑为独立按钮级权限；
 * 删除（system:user:delete）管理员经"全量菜单"自动获得，审核员保持只读。
 * 账号、邮箱唯一性等业务校验在 Service 层完成。
 */
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Resource
    private AdminUserService userService;

    /** 用户分页：keyword（账号/昵称/邮箱模糊）+ status + role 筛选 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:list')")
    public CommonResult<PageResult<AdminUserRespVO>> page(AdminUserPageReqVO pageReqVO) {
        return CommonResult.success(userService.getUserPage(pageReqVO));
    }

    /** 新增用户：账号、邮箱唯一，密码 MD5 入库，roles 非空时绑定角色 */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('system:user:create')")
    public CommonResult<Long> create(@RequestBody UserSaveReqVO createReqVO) {
        return CommonResult.success(userService.createUser(createReqVO));
    }

    /** 编辑用户：账号不可改，昵称/邮箱/状态按传值更新，roles 非空时全量重绑 */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('system:user:update')")
    public CommonResult<Boolean> update(@RequestBody UserSaveReqVO updateReqVO) {
        userService.updateUser(updateReqVO);
        return CommonResult.success(true);
    }

    /** 删除用户：物理删除并级联清理角色绑定；不允许删除当前登录账号自己 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        }
        if (loginUser.getId().equals(id)) {
            throw exception(USER_DELETE_SELF);
        }
        userService.deleteUser(id);
        return CommonResult.success(true);
    }
}
