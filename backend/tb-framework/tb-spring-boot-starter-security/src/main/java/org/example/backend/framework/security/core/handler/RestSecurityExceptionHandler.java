package org.example.backend.framework.security.core.handler;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.pojo.CommonResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级鉴权失败的处理：把 {@link AccessDeniedException} 转成 HTTP 403。
 *
 * <p>为什么不能只靠 {@code RestAccessDeniedHandler}：{@code @PreAuthorize} 的拒绝
 * 发生在 Controller 方法调用阶段，会先被 Spring MVC 的异常解析器处理；
 * 若落到 {@code GlobalExceptionHandler} 的兜底分支，就会变成 500。
 *
 * <p>优先级高于全局兜底处理器；仍返回统一的 {@code CommonResult} 结构。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class RestSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CommonResult<?> handleAccessDenied(AccessDeniedException ex) {
        log.info("[handleAccessDenied] 权限不足: {}", ex.getMessage());
        return CommonResult.error(GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                GlobalErrorCodeConstants.FORBIDDEN.getMsg());
    }

}
