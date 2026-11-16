package org.example.backend.module.knowledgebase.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 knowledgebase
 */
public interface ErrorCodeConstants {

    // ========== 知识库模块 1-003-000-000 ==========
    ErrorCode SPACE_NOT_EXISTS = new ErrorCode(1_003_000_000, "知识库空间不存在");
    ErrorCode DOCUMENT_NOT_EXISTS = new ErrorCode(1_003_000_001, "知识库文档不存在");
    ErrorCode DOCUMENT_ALREADY_COMPLETED = new ErrorCode(1_003_000_002, "知识库文档已完成向量化，不可重复提交");
    ErrorCode SPACE_NO_PERMISSION = new ErrorCode(1_003_000_003, "无权操作该知识库空间");
    ErrorCode SPACE_VISIBILITY_INVALID = new ErrorCode(1_003_000_004, "可见性取值无效，只能是 0（私有）或 1（公有）");

}
