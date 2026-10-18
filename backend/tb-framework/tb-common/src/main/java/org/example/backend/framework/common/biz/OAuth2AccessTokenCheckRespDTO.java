package org.example.backend.framework.common.biz;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class OAuth2AccessTokenCheckRespDTO {
    /**
     * 用户编号
     */
    private Long userId;
    /**
     * 角色编码集合（如 normal / member / admin / auditor）
     */
    private List<String> roles;
    /**
     * 权限码集合（来自 system_menu.permission）
     */
    private List<String> permissions;
    /**
     * 用户信息
     */
    private Map<String, String> userInfo;
    /**
     * 授权范围的数组
     */
    private List<String> scopes;
    /**
     * 过期时间
     */
    private LocalDateTime expiresTime;
}
