package org.example.backend.module.test.model.vo;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AuthLoginRespVO {
    private Long userId;

    /** 用户身份：0=普通用户，1=会员，2=管理员（登录时即返回，避免再发身份查询接口） */
    private Integer userType;

    private String accessToken;

    private String refreshToken;

    private LocalDateTime expiresTime;
}
