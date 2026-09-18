package org.example.backend.module.knowledgebase.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.model.vo.SpaceRespVO;
import org.example.backend.module.knowledgebase.model.vo.SpaceSaveReqVO;
import org.example.backend.module.knowledgebase.service.KbSpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.SPACE_NOT_EXISTS;

@Service
public class KbSpaceServiceImpl implements KbSpaceService {

    @Resource
    private KbSpaceMapper spaceMapper;
    @Resource
    private KbDocumentMapper documentMapper;
    @Resource
    private KbDocumentChunkMapper chunkMapper;

    @Override
    public List<SpaceRespVO> getSpaceList() {
        return spaceMapper.selectRespList();
    }

    @Override
    public Long createSpace(SpaceSaveReqVO reqVO) {
        KbSpaceDO space = new KbSpaceDO();
        space.setName(reqVO.getName());
        space.setDescription(reqVO.getDescription());
        spaceMapper.insert(space);
        return space.getId();
    }

    @Override
    public SpaceRespVO updateSpace(SpaceSaveReqVO reqVO) {
        if (spaceMapper.selectById(reqVO.getId()) == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        KbSpaceDO update = new KbSpaceDO();
        update.setId(reqVO.getId());
        update.setName(reqVO.getName());
        update.setDescription(reqVO.getDescription());
        spaceMapper.updateById(update);
        return buildRespVO(spaceMapper.selectById(reqVO.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpace(Long id) {
        if (spaceMapper.selectById(id) == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        // 级联逻辑删除：分片 -> 文档 -> 空间
        List<KbDocumentDO> documents = documentMapper.selectListBySpaceId(id);
        documents.forEach(document -> chunkMapper.deleteByDocumentId(document.getId()));
        documentMapper.deleteBySpaceId(id);
        spaceMapper.deleteById(id);
    }

    @Override
    public String getSpaceName(Long id) {
        if (id == null) {
            return null;
        }
        KbSpaceDO space = spaceMapper.selectById(id);
        return space == null ? null : space.getName();
    }

    private SpaceRespVO buildRespVO(KbSpaceDO space) {
        SpaceRespVO vo = new SpaceRespVO();
        vo.setId(space.getId());
        vo.setName(space.getName());
        vo.setDescription(space.getDescription());
        vo.setCreateTime(space.getCreateTime());
        vo.setDocumentCount(spaceMapper.selectDocumentCount(space.getId()));
        return vo;
    }

}
