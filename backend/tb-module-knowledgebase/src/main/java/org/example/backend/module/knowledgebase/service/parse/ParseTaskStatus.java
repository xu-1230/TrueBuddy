package org.example.backend.module.knowledgebase.service.parse;

import lombok.Data;

/**
 * 文档解析任务状态（与解析引擎无关的统一抽象）。
 */
@Data
public class ParseTaskStatus {

    /** 任务状态：排队中 / 解析中 / 成功 / 失败 */
    public enum State {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }

    private State state;

    /** 解析进度 0-100 */
    private int progress;

    /** 失败原因（state=FAILED 时有值） */
    private String error;

    /** 页数（引擎返回时才有值） */
    private Integer pageCount;

    /** 表格数（引擎返回时才有值） */
    private Integer tableCount;

    /** 图片数（引擎返回时才有值） */
    private Integer imageCount;

    public static ParseTaskStatus pending() {
        ParseTaskStatus status = new ParseTaskStatus();
        status.setState(State.PENDING);
        status.setProgress(0);
        return status;
    }

    public static ParseTaskStatus processing(int progress) {
        ParseTaskStatus status = new ParseTaskStatus();
        status.setState(State.PROCESSING);
        status.setProgress(Math.max(0, Math.min(99, progress)));
        return status;
    }

    public static ParseTaskStatus success() {
        ParseTaskStatus status = new ParseTaskStatus();
        status.setState(State.SUCCESS);
        status.setProgress(100);
        return status;
    }

    public static ParseTaskStatus failed(String error) {
        ParseTaskStatus status = new ParseTaskStatus();
        status.setState(State.FAILED);
        status.setProgress(0);
        status.setError(error);
        return status;
    }
}
