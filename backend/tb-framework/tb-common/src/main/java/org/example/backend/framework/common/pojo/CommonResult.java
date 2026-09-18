package org.example.backend.framework.common.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果包装：{ code, message, data }
 * <p>
 * 约定：code = 0 表示成功，非 0 表示失败，message 为前端可直接展示的错误描述。
 */
@Data
public class CommonResult<T> implements Serializable {

    /** 成功码 */
    public static final int CODE_SUCCESS = 0;
    /** 服务器内部错误 */
    public static final int CODE_INTERNAL_ERROR = 500;

    private int code;
    private String message;
    private T data;

    public static <T> CommonResult<T> success(T data) {
        CommonResult<T> result = new CommonResult<>();
        result.code = CODE_SUCCESS;
        result.message = "";
        result.data = data;
        return result;
    }

    public static <T> CommonResult<T> error(int code, String message) {
        CommonResult<T> result = new CommonResult<>();
        result.code = code;
        result.message = message;
        return result;
    }
}
