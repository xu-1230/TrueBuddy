package org.example.backend.module.test.model.vo;

import lombok.Data;

/**
 * 管理端用户分页查询请求
 *
 * <p>keyword 模糊匹配 账号 / 昵称 / 邮箱；status、role 为空时不过滤。
 */
@Data
public class AdminUserPageReqVO {

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 10;

    /** 关键词 */
    private String keyword;

    /** 激活状态：0=正常，1=禁用；空为全部 */
    private Integer status;

    /** 角色编码；空为全部 */
    private String role;
}
