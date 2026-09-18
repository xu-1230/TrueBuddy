package org.example.backend.module.infra.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 infra
 */
public interface ErrorCodeConstants {

    // ========== 基础设施模块 1-001-000-000 ==========
    ErrorCode FILE_EMPTY = new ErrorCode(1_001_000_000, "上传文件不能为空");
    ErrorCode FILE_SIZE_EXCEED = new ErrorCode(1_001_000_001, "文件大小超过上限（50MB）");
    ErrorCode FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_001_000_002, "不支持的文件类型");

}
