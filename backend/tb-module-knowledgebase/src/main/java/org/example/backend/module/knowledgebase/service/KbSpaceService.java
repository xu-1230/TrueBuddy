package org.example.backend.module.knowledgebase.service;

import org.example.backend.module.knowledgebase.model.vo.SpaceRespVO;
import org.example.backend.module.knowledgebase.model.vo.SpaceSaveReqVO;

import java.util.List;

/**
 * 知识库空间 Service
 */
public interface KbSpaceService {

    /** 查询空间列表（含文档数量） */
    List<SpaceRespVO> getSpaceList();

    /** 创建空间，返回空间 id */
    Long createSpace(SpaceSaveReqVO reqVO);

    /** 修改空间，返回更新后的空间 */
    SpaceRespVO updateSpace(SpaceSaveReqVO reqVO);

    /** 删除空间（级联删除空间下文档及其分片） */
    void deleteSpace(Long id);

    /**
     * 查询空间名称（供其它模块引用展示，避免跨模块暴露 DO）
     *
     * @param id 空间 id，可为空
     * @return 空间名称；空间不存在或 id 为空时返回 null
     */
    String getSpaceName(Long id);

}
