package org.example.backend.module.test.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理端当前账号资料（{@code GET /admin/auth/profile}）。
 *
 * <p>角色与权限由服务端实时解析，前端不再自行拼装或持久化。
 */
@Data
public class AdminProfileRespVO {

    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    /** 角色编码集合，如 admin / auditor */
    private List<String> roles;
    /** 权限码集合，如 system:user:list */
    private List<String> permissions;

}
