package org.example.backend.module.test.model;

import lombok.Data;

/**
 * 菜单权限行（{@code system_menu}）。
 *
 * <p>RBAC 的权限码唯一来源：角色通过 {@code system_role_menu} 关联菜单，
 * 用户通过 {@code system_user_role} 关联角色，最终得到 {@code permission} 集合。
 */
@Data
public class MenuDO {

    private Long id;
    private String name;
    private Long parentId;
    /** 类型：1=目录，2=菜单，3=按钮 */
    private Integer type;
    private String path;
    private String component;
    /** 权限标识，如 {@code system:user:list} */
    private String permission;
    private String icon;
    private Integer sort;
    /** 状态：0=正常，1=禁用 */
    private Integer status;
    /** 是否在侧栏可见 */
    private Boolean visible;

}
