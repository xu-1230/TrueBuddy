package org.example.backend.module.knowledgebase.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 knowledgebase
 */
public interface ErrorCodeConstants {

    // ========== 知识库模块 1-003-000-000 ==========
    ErrorCode SPACE_NOT_EXISTS = new ErrorCode(1_003_000_000, "知识库空间不存在");
    ErrorCode DOCUMENT_NOT_EXISTS = new ErrorCode(1_003_000_001, "知识库文档不存在");

}
