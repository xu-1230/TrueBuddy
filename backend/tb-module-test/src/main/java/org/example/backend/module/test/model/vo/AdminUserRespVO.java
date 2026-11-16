package org.example.backend.module.test.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端用户列表行 VO
 *
 * <p>字段与前端用户列表表格列一一对应（账号 / 昵称 / ID / 邮箱 / 激活状态 / 登录IP），
 * roles 供详情弹窗与角色筛选展示。不含 password 等敏感字段。
 */
@Data
public class AdminUserRespVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    /** 激活状态：0=正常，1=禁用 */
    private Integer status;

    /** 最后登录 IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

    /** 绑定的角色编码（来源 system_user_role + system_role） */
    private List<String> roles = new ArrayList<>();
}
