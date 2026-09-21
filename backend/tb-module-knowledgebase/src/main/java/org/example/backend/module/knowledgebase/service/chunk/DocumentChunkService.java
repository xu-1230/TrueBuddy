package org.example.backend.module.knowledgebase.service.chunk;

import java.util.List;

/**
 * 文档分片服务：把解析出的连续文本块按语义划分为若干分片。
 *
 * <p>分片是向量化的最小单位，因此要求分片内部语义自洽、可独立检索。
 */
public interface DocumentChunkService {

    /**
     * 将解析出的连续文本块按语义划分为若干分片。
     *
     * @param blocks 按阅读顺序排列的文本块（调用方需已过滤空块）
     * @return 分片正文列表，按原文档顺序排列；输入为空时返回空列表
     */
    List<String> chunk(List<String> blocks);

}
