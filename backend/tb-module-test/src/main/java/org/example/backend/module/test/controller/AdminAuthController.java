package org.example.backend.module.test.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.AdminProfileRespVO;
import org.example.backend.module.test.service.AdminUserService;
import org.example.backend.module.test.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test.enums.ErrorCodeConstants.USER_NOT_EXISTS;

/**
 * 管理端身份接口：当前账号资料与权限码。
 *
 * <p>两个接口都要求 {@code admin:dashboard}：管理员与审核员拥有该权限，
 * 普通用户与会员会被 403 拦下。真正的安全边界在这里，前端过滤只负责界面可达性。
 */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    @Resource
    private AdminUserService adminUserService;

    @Resource
    private PermissionService permissionService;

    /** 当前管理端账号的资料 + 角色 + 权限 */
    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('admin:dashboard')")
    public CommonResult<AdminProfileRespVO> profile() {
        LoginUser loginUser = requireLoginUser();
        AdminUserDO user = adminUserService.getUser(loginUser.getId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        AdminProfileRespVO respVO = new AdminProfileRespVO();
        respVO.setUserId(user.getId());
        respVO.setUsername(user.getUsername());
        respVO.setNickname(user.getNickname());
        respVO.setAvatar(user.getAvatar());
        respVO.setRoles(permissionService.getRoleCodes(user.getId()));
        respVO.setPermissions(permissionService.getPermissions(user.getId()));
        return CommonResult.success(respVO);
    }

    /** 当前账号的权限码（供前端刷新或单独拉取） */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('admin:dashboard')")
    public CommonResult<List<String>> permissions() {
        LoginUser loginUser = requireLoginUser();
        return CommonResult.success(permissionService.getPermissions(loginUser.getId()));
    }

    private static LoginUser requireLoginUser() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        }
        return loginUser;
    }

}
