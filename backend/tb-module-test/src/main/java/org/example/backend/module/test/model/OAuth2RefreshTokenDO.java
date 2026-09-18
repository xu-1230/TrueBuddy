package org.example.backend.module.test.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class OAuth2RefreshTokenDO {
    private Long id;
    private String refreshToken;
    private Long userId;
    private Integer userType;
    private List<String> scopes;
    private LocalDateTime expiresTime;

}