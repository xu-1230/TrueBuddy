package org.example.backend.module.test2.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 test2
 */
public interface ErrorCodeConstants {

    // ========== 测试模块2（文档解析验证）1_006_000_000 ==========
    ErrorCode TEST2_FILE_LIST_EMPTY = new ErrorCode(1_006_000_000, "作业文件清单不能为空");

}
