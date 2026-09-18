package org.example.backend.module.knowledgebase.service;

import org.example.backend.module.knowledgebase.model.vo.ChunkRespVO;

import java.util.List;

/**
 * 知识库文档分片 Service
 */
public interface KbDocumentChunkService {

    /** 查询指定文档的分片列表 */
    List<ChunkRespVO> getChunkList(Long documentId);

}
