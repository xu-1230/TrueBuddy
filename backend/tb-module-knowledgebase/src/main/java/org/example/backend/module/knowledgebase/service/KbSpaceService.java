package org.example.backend.module.knowledgebase.service;

import org.example.backend.module.knowledgebase.model.vo.PublicSpaceRespVO;
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
     * 切换空间可见性（0=私有 / 1=公有），仅创建者可调。
     *
     * @param id 空间 id
     * @param visibility 目标可见性，只能是 0 或 1
     */
    void updateSpaceVisibility(Long id, Integer visibility);

    /**
     * 查询他人的公有空间列表（「公有库」浏览区）。
     *
     * <p>只返回 {@code visibility=1} 且创建者不是当前登录用户的空间；
     * 公有对他人只读，fork 入口由共享链路提供。
     */
    List<PublicSpaceRespVO> getPublicSpaceList();

    /**
     * fork 空间为「添加为自己的」独立副本（US16）：复制空间 → 已向量化文档 → 版面块 →
     * 分片 → 向量，单事务原子完成；副本归属当前用户、私有，命名「原名（副本）」，
     * 与原库此后无任何同步。
     *
     * @param id 源空间 id（本人的或他人公有的）
     * @return 副本空间 id
     */
    Long forkSpace(Long id);

    /**
     * 查询空间名称（供其它模块引用展示，避免跨模块暴露 DO）
     *
     * @param id 空间 id，可为空
     * @return 空间名称；空间不存在或 id 为空时返回 null
     */
    String getSpaceName(Long id);

}
