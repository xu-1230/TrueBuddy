package org.example.backend.module.test.model.vo;

import lombok.Data;

/**
 * 刷新令牌请求（{@code POST /system/auth/refresh}）。
 */
@Data
public class AuthRefreshReqVO {

    /** 登录时下发的 refreshToken；一次性使用，刷新成功后旧值立即失效 */
    private String refreshToken;

}
