package org.example.backend.framework.web.core.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.example.backend.framework.common.pojo.CommonResult.error;

/**
 * 全局异常处理器：将异常统一转换为 {@link CommonResult}
 * <p>
 * 约定：业务异常返回其自身错误码与提示信息；系统异常统一返回 500 且不泄漏堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：message 为前端可直接展示的错误描述 */
    @ExceptionHandler(ServiceException.class)
    public CommonResult<?> serviceExceptionHandler(ServiceException ex) {
        log.info("[serviceExceptionHandler] 业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return error(ex.getCode(), ex.getMessage());
    }

    /** 兜底异常：避免异常细节泄漏给前端 */
    @ExceptionHandler(Exception.class)
    public CommonResult<?> defaultExceptionHandler(HttpServletRequest request, Throwable ex) {
        log.error("[defaultExceptionHandler] 系统异常: uri={}", request.getRequestURI(), ex);
        return error(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getMsg());
    }

}
