package org.example.backend.module.workflow.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码常量：模块 workflow。
 *
 * <p>号段接续既有模块（infra=1_001、test=1_002、knowledgebase=1_003、doctemplate=1_004），
 * 本模块使用 {@code 1_005_xxx}。
 */
public interface ErrorCodeConstants {

    // ========== 自定义工作流模块 1-005-000-000 ==========
    ErrorCode WORKFLOW_SCHEMA_EMPTY = new ErrorCode(1_005_000_000, "工作流定义为空，无法执行");
    ErrorCode WORKFLOW_SCHEMA_INVALID = new ErrorCode(1_005_000_001, "工作流定义解析失败：{}");
    ErrorCode WORKFLOW_NODE_TYPE_UNSUPPORTED = new ErrorCode(1_005_000_002, "不支持的工作流节点类型：{}");
    ErrorCode WORKFLOW_GRAPH_BUILD_FAILED = new ErrorCode(1_005_000_003, "工作流建图失败：{}");
    ErrorCode WORKFLOW_RUN_FAILED = new ErrorCode(1_005_000_004, "工作流执行失败：{}");
    ErrorCode WORKFLOW_SESSION_NOT_FOUND = new ErrorCode(1_005_000_005, "工作流会话不存在或已过期，请重新发起");
    ErrorCode WORKFLOW_INPUT_REQUIRED = new ErrorCode(1_005_000_007, "缺少必填的工作流输入：{}");
    ErrorCode WORKFLOW_SCHEMA_JSON_INVALID = new ErrorCode(1_005_000_010, "工作流定义 JSON 格式不正确：{}");
    ErrorCode WORKFLOW_NOT_FOUND = new ErrorCode(1_005_000_011, "工作流不存在或已被删除");
    ErrorCode WORKFLOW_SCHEMA_REQUIRED = new ErrorCode(1_005_000_013, "工作流画布内容不能为空");
    ErrorCode WORKFLOW_NOT_WAITING_CONFIRM = new ErrorCode(1_005_000_015, "工作流当前不处于等待确认状态：{}");
    ErrorCode WORKFLOW_CONFIRM_PLAN_EMPTY = new ErrorCode(1_005_000_016, "确认后的批改清单不能为空");


}
