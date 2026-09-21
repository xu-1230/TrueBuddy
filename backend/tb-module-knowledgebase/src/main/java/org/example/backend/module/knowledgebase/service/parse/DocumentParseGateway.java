package org.example.backend.module.knowledgebase.service.parse;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文档解析网关
 * 统一为「提交任务 → 查询状态 → 拉取 Markdown」三段式。
 *
 */
public interface DocumentParseGateway {

    /**
     * 提交解析任务。
     *
     * @param inputStream 文件流（实现内部应在当前线程读完，避免异步读取时流已关闭）
     * @param fileName    带扩展名的文件名，云端解析器依赖它路由
     * @return 任务句柄（jobId），用于后续查询状态与拉取结果
     */
    String submit(InputStream inputStream, String fileName);

    /**
     * 查询任务状态。
     *
     * @param jobId 提交时返回的任务句柄
     * @return 统一状态对象
     */
    ParseTaskStatus query(String jobId);

    /**
     * 拉取解析结果（Markdown）。仅在 {@link ParseTaskStatus.State#SUCCESS} 后调用。
     *
     * @param jobId 提交时返回的任务句柄
     * @return Markdown 全文
     */
    List<Map<?, ?>> fetchMarkdown(String jobId);

    /**
     * 释放任务占用的临时资源（云端解析无状态，默认空实现）。
     */
    default void release(String jobId) {
        // 默认无需释放
    }
}
