package org.example.backend.module.knowledgebase.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.knowledgebase.enums.ErrorCodeConstants;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbDocumentChunkDO;
import org.example.backend.module.knowledgebase.model.vo.ChunkRespVO;
import org.example.backend.module.knowledgebase.service.KbDocumentChunkService;
import org.example.backend.module.knowledgebase.service.KbSpaceAccess;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 分片列表的实现。
 *
 * <p>分片是文档正文内容的读取口径（切分后的文本块），与文档列表共用同一把可读闸：
 * 「创建者本人 或 公有空间」（US15）。没有这把闸时，私有空间的分片可被任何登录用户凭
 * {@code documentId} 枚举读走——公有库浏览区又把他人文档 id 暴露给浏览者，该路径必须锁死。
 */
@Service
public class KbDocumentChunkServiceImpl implements KbDocumentChunkService {

    @Resource
    private KbDocumentChunkMapper chunkMapper;
    @Resource
    private KbDocumentMapper documentMapper;
    @Resource
    private KbSpaceMapper spaceMapper;

    @Override
    public List<ChunkRespVO> getChunkList(Long documentId) {
        KbDocumentDO document = documentId == null ? null : documentMapper.selectById(documentId);
        if (document == null) {
            throw exception(ErrorCodeConstants.DOCUMENT_NOT_EXISTS);
        }
        KbSpaceAccess.requireReadable(
                KbSpaceAccess.requireExisting(spaceMapper, document.getSpaceId()), currentUserId());
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

    /** 当前登录用户 id（/knowledge-base/** 全部要求登录，调用侧必然已登录） */
    private Long currentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getId();
    }

}
