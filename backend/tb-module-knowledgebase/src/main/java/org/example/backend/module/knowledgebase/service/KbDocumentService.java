package org.example.backend.module.knowledgebase.service;

import org.example.backend.module.knowledgebase.model.vo.DocumentRespVO;
import org.example.backend.module.knowledgebase.model.vo.DocumentSaveReqVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库文档 Service
 */
public interface KbDocumentService {

    /** 查询指定空间下的文档列表 */
    List<DocumentRespVO> getDocumentList(Long spaceId);

    /** 新增文档（仅元数据），返回文档 id */
    Long createDocument(DocumentSaveReqVO reqVO);

    /** 删除文档（级联删除文档分片，并清理向量库） */
    void deleteDocument(Long id);

    /**
     * 用户放弃未完成向量化的文档：逻辑删除该文档及其分片（假删除），并清理向量库。
     *
     * <p>若文档已完成向量化（status=2），视为最终结果，不执行任何操作。
     *
     * @param documentId 文档 id
     */
    void abandonDocument(Long documentId);

    /**
     * 第一步：文件列表上传并提交解析任务（MinIO 存储 + 落库 + 提交解析引擎）。
     *
     * @param files 文件列表（一次可上传多个文件）
     * @return 每个文件的提交结果列表：[{documentId, fileName, fileSize, type, engine, status}]
     */
    List<Map<String, Object>> uploadAndSubmit(List<MultipartFile> files, Long spaceId);

    /**
     * 第二步：查询解析状态；解析完成后落库分片并返回分片预览。
     *
     * @return {documentId, status, progress, totalChunks, chunks:[{index,type,content}], error}
     */
    Map<String, Object> getParseStatus(Long documentId);

    /**
     * 第三步：确认分片后向量化（覆盖落库分片 + 写入向量库）。
     *
     * @param documentId 文档 id
     * @param spaceId    所属知识库空间 id
     * @param chunksJson 前端确认的分片列表 JSON
     * @return {documentId, savedChunks, status}
     */
    Map<String, Object> vectorize(Long documentId, Long spaceId, String chunksJson);

    /**
     * 检索增强：按空间检索与 query 最相关的分片，拼接为上下文文本。
     *
     * @param spaceId 空间 id（为空返回空串）
     * @param query   检索词（为空返回空串）
     * @param topK    返回分片数量
     * @return 拼接后的上下文文本（无命中或检索异常时返回空串）
     */
    String searchBySpace(Long spaceId, String query, int topK);

}
