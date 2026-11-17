package org.example.backend.module.test.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前登录用户资料（{@code GET /system/auth/profile}）。
 *
 * <p>与 {@link AdminProfileRespVO} 的区别：本 VO 只要求登录，不要求 admin:dashboard，
 * 供用户端页脚等场景读取昵称 / 头像 / 角色，不返回权限码等管理端信息。
 */
@Data
public class UserProfileRespVO {

    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    /** 角色编码集合（只含启用角色），如 normal / member / admin / auditor */
    private List<String> roles;

}
