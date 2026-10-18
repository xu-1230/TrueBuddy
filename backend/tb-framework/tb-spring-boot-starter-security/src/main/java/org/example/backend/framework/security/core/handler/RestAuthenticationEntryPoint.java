package org.example.backend.framework.security.core.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 未登录处理器：返回 HTTP 401 与统一的 {@code CommonResult} JSON。
 *
 * <p>之前 {@code exceptionHandling} 被注释掉，未认证请求落到 Spring 默认入口，
 * 前端无法区分“未登录”与“无权限”。
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
    }

    static void write(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }

}
