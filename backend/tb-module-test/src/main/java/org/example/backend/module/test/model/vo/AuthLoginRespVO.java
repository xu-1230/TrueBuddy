package org.example.backend.module.test.model.vo;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AuthLoginRespVO {
    private Long userId;

    private String accessToken;

    private String refreshToken;

    private LocalDateTime expiresTime;
}
