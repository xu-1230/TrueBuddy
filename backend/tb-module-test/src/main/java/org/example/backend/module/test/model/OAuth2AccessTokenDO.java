package org.example.backend.module.test.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class OAuth2AccessTokenDO{
    private Long id;
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private Map<String, String> userInfo;
    private List<String> scopes;
    private LocalDateTime expiresTime;

}