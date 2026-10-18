package org.example.backend.module.test.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Data
public class AuthLoginRespVO {
    private Long userId;

    /** 角色编码集合（只含启用角色），如 normal / member / admin / auditor */
    private List<String> roles;

    /** 权限码集合（来自 system_menu.permission，角色与菜单均需启用） */
    private List<String> permissions;

    private String accessToken;

    private String refreshToken;

    private LocalDateTime expiresTime;
}
