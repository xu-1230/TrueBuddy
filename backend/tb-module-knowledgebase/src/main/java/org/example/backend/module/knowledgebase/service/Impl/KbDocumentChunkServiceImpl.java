package org.example.backend.module.knowledgebase.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentChunkDO;
import org.example.backend.module.knowledgebase.model.vo.ChunkRespVO;
import org.example.backend.module.knowledgebase.service.KbDocumentChunkService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KbDocumentChunkServiceImpl implements KbDocumentChunkService {

    @Resource
    private KbDocumentChunkMapper chunkMapper;

    @Override
    public List<ChunkRespVO> getChunkList(Long documentId) {
        return chunkMapper.selectListByDocumentId(documentId).stream().map(this::buildRespVO).toList();
    }

    private ChunkRespVO buildRespVO(KbDocumentChunkDO chunk) {
        ChunkRespVO vo = new ChunkRespVO();
        vo.setId(chunk.getId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        return vo;
    }

}
