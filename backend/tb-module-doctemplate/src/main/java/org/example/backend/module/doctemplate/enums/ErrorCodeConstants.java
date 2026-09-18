package org.example.backend.module.doctemplate.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 doctemplate
 */
public interface ErrorCodeConstants {

    // ========== 文档模板生成模块 1-004-000-000 ==========
    ErrorCode TEMPLATE_FILE_ID_REQUIRED = new ErrorCode(1_004_000_000, "请先上传模板文档");
    ErrorCode DESCRIPTION_REQUIRED = new ErrorCode(1_004_000_001, "请填写 AI 描述说明");
    ErrorCode DESCRIPTION_TOO_LONG = new ErrorCode(1_004_000_002, "AI 描述说明不能超过 500 字");
    ErrorCode TEMPLATE_NOT_EXISTS = new ErrorCode(1_004_000_003, "模板文件不存在或已被删除");
    ErrorCode TEMPLATE_TYPE_UNSUPPORTED = new ErrorCode(1_004_000_004, "仅支持 .docx 模板文档");
    ErrorCode GENERATION_NOT_EXISTS = new ErrorCode(1_004_000_005, "生成记录不存在");
    ErrorCode GENERATE_FAILED = new ErrorCode(1_004_000_006, "文档生成失败：{}");
    ErrorCode MCP_TOOL_UNAVAILABLE = new ErrorCode(1_004_000_007, "文档处理服务（docx-mcp）不可用：{}");
    ErrorCode AI_RESPONSE_INVALID = new ErrorCode(1_004_000_008, "AI 返回内容解析失败：{}");

}
