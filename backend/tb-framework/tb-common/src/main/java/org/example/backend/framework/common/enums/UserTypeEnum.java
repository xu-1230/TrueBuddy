package org.example.backend.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户类型枚举
 */
@Getter
@AllArgsConstructor
public enum UserTypeEnum {

    NORMAL(0, "普通用户"),
    MEMBER(1, "会员"),
    ADMIN(2, "管理员");

    private final Integer value;
    private final String name;
}