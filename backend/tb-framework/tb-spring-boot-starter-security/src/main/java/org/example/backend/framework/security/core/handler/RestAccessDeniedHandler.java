package org.example.backend.framework.security.core.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 无权限处理器：返回 HTTP 403 与统一的 {@code CommonResult} JSON。
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        RestAuthenticationEntryPoint.write(response, GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                GlobalErrorCodeConstants.FORBIDDEN.getMsg());
    }

}
