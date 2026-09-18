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

    /** 删除文档（级联删除文档分片） */
    void deleteDocument(Long id);

    /**
     * 上传文档并解析预览分片（不落库、不存 MinIO）
     *
     * @return {fileName, fileSize, type, totalChunks, chunks:[{index,type,content}]}
     */
    Map<String, Object> uploadAndPreview(MultipartFile file);

    /**
     * 向量化：上传文件到 MinIO + 保存文档/分片 + 写入向量库
     *
     * @param file      原始文件
     * @param chunksJson 前端确认的分片列表 JSON
     * @param spaceId   所属知识库空间 id
     * @return {documentId, savedChunks, status}
     */
    Map<String, Object> vectorize(MultipartFile file, String chunksJson, Long spaceId);

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
