package org.example.backend.module.homework.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 homework
 */
public interface ErrorCodeConstants {

    // ========== 作业批改模块 1-004-000-000 ==========
    ErrorCode HOMEWORK_FILE_EMPTY = new ErrorCode(1_004_000_000, "上传文件不能为空");
    ErrorCode HOMEWORK_PATH_COUNT_MISMATCH = new ErrorCode(1_004_000_001, "文件与相对路径数量不一致");
    ErrorCode HOMEWORK_PATH_INVALID = new ErrorCode(1_004_000_002, "文件相对路径不合法");
    ErrorCode HOMEWORK_FILE_COUNT_EXCEED = new ErrorCode(1_004_000_003, "单次批改最多 50 份作业");
    /** 错误信息带出错文件的相对路径，便于批量上传时定位 */
    ErrorCode HOMEWORK_FILE_SIZE_EXCEED = new ErrorCode(1_004_000_004, "文件大小超过上限（50MB）：{}");
    /** 错误信息带出错文件的相对路径，便于批量上传时定位 */
    ErrorCode HOMEWORK_FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_004_000_005, "不支持的文件类型：{}");
    ErrorCode HOMEWORK_TASK_NOT_EXISTS = new ErrorCode(1_004_000_006, "批改任务不存在");
    ErrorCode HOMEWORK_TASK_STATUS_INVALID = new ErrorCode(1_004_000_007, "任务不是待批改状态，无法开始批改");
    /** 错误信息带具体原因（评分区间 / 自定义导出列校验），前端直接展示 */
    ErrorCode HOMEWORK_GRADE_CONFIG_INVALID = new ErrorCode(1_004_000_008, "批改配置不合法：{}");

}
