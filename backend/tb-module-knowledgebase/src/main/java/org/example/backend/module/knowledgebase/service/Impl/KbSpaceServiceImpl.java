package org.example.backend.module.knowledgebase.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentParseBlockMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.model.vo.PublicSpaceRespVO;
import org.example.backend.module.knowledgebase.model.vo.SpaceRespVO;
import org.example.backend.module.knowledgebase.model.vo.SpaceSaveReqVO;
import org.example.backend.module.knowledgebase.service.KbSpaceAccess;
import org.example.backend.module.knowledgebase.service.KbSpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.SPACE_NO_PERMISSION;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.SPACE_NOT_EXISTS;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.SPACE_VISIBILITY_INVALID;

@Service
public class KbSpaceServiceImpl implements KbSpaceService {

    /** 可见性：私有（ADR 0006：新建一律私有，公有能力在共享链路打开） */
    private static final int VISIBILITY_PRIVATE = 0;
    /** 可见性：公有 */
    private static final int VISIBILITY_PUBLIC = 1;
    /** 文档状态：已完成向量化（fork 只带入这一状态的文档） */
    private static final int STATUS_COMPLETED = 2;
    /** fork 副本命名的序号上限（防止极端同名堆积导致死循环） */
    private static final int FORK_NAME_MAX_RETRY = 99;

    @Resource
    private KbSpaceMapper spaceMapper;
    @Resource
    private KbDocumentMapper documentMapper;
    @Resource
    private KbDocumentChunkMapper chunkMapper;
    @Resource
    private KbDocumentParseBlockMapper parseBlockMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public List<SpaceRespVO> getSpaceList() {
        return spaceMapper.selectRespList(currentUserId());
    }

    @Override
    public Long createSpace(SpaceSaveReqVO reqVO) {
        KbSpaceDO space = new KbSpaceDO();
        space.setName(reqVO.getName());
        space.setDescription(reqVO.getDescription());
        space.setVisibility(VISIBILITY_PRIVATE);
        space.setCreator(currentUserId());
        spaceMapper.insert(space);
        return space.getId();
    }

    @Override
    public SpaceRespVO updateSpace(SpaceSaveReqVO reqVO) {
        KbSpaceDO existing = spaceMapper.selectById(reqVO.getId());
        if (existing == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        assertSpaceOwner(existing);
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
        KbSpaceDO existing = spaceMapper.selectById(id);
        if (existing == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        assertSpaceOwner(existing);
        // 级联逻辑删除：分片 -> 文档 -> 空间
        List<KbDocumentDO> documents = documentMapper.selectListBySpaceId(id);
        documents.forEach(document -> chunkMapper.deleteByDocumentId(document.getId()));
        documentMapper.deleteBySpaceId(id);
        spaceMapper.deleteById(id);
    }

    @Override
    public void updateSpaceVisibility(Long id, Integer visibility) {
        if (visibility == null || (visibility != VISIBILITY_PRIVATE && visibility != VISIBILITY_PUBLIC)) {
            throw exception(SPACE_VISIBILITY_INVALID);
        }
        KbSpaceDO existing = spaceMapper.selectById(id);
        if (existing == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        // 公有/私有切换同样是归属写操作：非创建者即使面对公有空间也拒绝
        assertSpaceOwner(existing);
        spaceMapper.updateVisibility(id, visibility);
    }

    @Override
    public List<PublicSpaceRespVO> getPublicSpaceList() {
        return spaceMapper.selectPublicList(currentUserId());
    }

    @Override
    public Long forkSpace(Long id) {
        // 与公有只读同一把闸（口径统一在 KbSpaceAccess）：本人空间或他人公有可 fork，产出写给自己
        Long userId = currentUserId();
        KbSpaceDO source = KbSpaceAccess.requireExisting(spaceMapper, id);
        KbSpaceAccess.requireReadable(source, userId);

        // 单事务原子完成：空间 + 文档 + 版面块 + 分片 + 向量要么一起生成，要么整笔回滚
        return transactionTemplate.execute(txStatus -> {
            KbSpaceDO copy = new KbSpaceDO();
            copy.setName(uniqueCopyNameOf(source.getName()));
            copy.setDescription(source.getDescription());
            copy.setVisibility(VISIBILITY_PRIVATE);
            copy.setCreator(userId);
            spaceMapper.insert(copy);

            List<KbDocumentDO> sourceDocs = documentMapper.selectCompletedListBySpaceId(id);
            for (KbDocumentDO document : sourceDocs) {
                copyDocumentWithChunks(document, copy.getId());
            }
            return copy.getId();
        });
    }

    /** 复制单篇已向量化文档及其版面块 / 分片 / 向量，并做行数对账（不完整即抛出回滚） */
    private void copyDocumentWithChunks(KbDocumentDO document, Long newSpaceId) {
        KbDocumentDO copyDoc = new KbDocumentDO();
        copyDoc.setSpaceId(newSpaceId);
        // 物理文件共享引用：infra_file 行与 MinIO 对象不动，副本文档只挂同一个 file_id
        copyDoc.setFileId(document.getFileId());
        copyDoc.setName(document.getName());
        copyDoc.setType(document.getType());
        copyDoc.setChunkCount(document.getChunkCount());
        // 副本文档直置已完成：分片与向量由复制链一并带入，不重走解析链路
        copyDoc.setStatus(STATUS_COMPLETED);
        copyDoc.setParseProgress(100);
        documentMapper.insert(copyDoc);

        parseBlockMapper.copyBlocks(document.getId(), copyDoc.getId(), newSpaceId);
        int copiedChunks = chunkMapper.copyChunks(document.getId(), copyDoc.getId(), newSpaceId);
        Integer expectedChunks = document.getChunkCount();
        if (expectedChunks != null && copiedChunks < expectedChunks) {
            throw new IllegalStateException("fork 分片复制不完整: documentId=" + document.getId()
                    + ", expected=" + expectedChunks + ", copied=" + copiedChunks);
        }

        long sourceVectors = chunkMapper.countVectorsByDocument(document.getId());
        int copiedVectors = chunkMapper.copyVectors(document.getId(), copyDoc.getId(), newSpaceId);
        if (copiedVectors < sourceVectors) {
            throw new IllegalStateException("fork 向量复制不完整: documentId=" + document.getId()
                    + ", expected=" + sourceVectors + ", copied=" + copiedVectors);
        }
    }

    /** 空间名称列宽（与 kb_space.name 的 varchar(100) 对齐）：原名过长时截断基底再接副本后缀 */
    private static final int NAME_MAX_LENGTH = 100;

    /** 副本命名：默认「原名（副本）」，被占用则追加序号（唯一索引 uk_kb_space_name 防撞） */
    private String uniqueCopyNameOf(String sourceName) {
        String base = sourceName == null || sourceName.isBlank() ? "未命名空间" : sourceName.trim();
        for (int seq = 1; seq <= FORK_NAME_MAX_RETRY; seq++) {
            String suffix = seq == 1 ? "（副本）" : "（副本 " + seq + "）";
            int allowed = NAME_MAX_LENGTH - suffix.length();
            String candidate = (base.length() > allowed ? base.substring(0, allowed) : base) + suffix;
            if (spaceMapper.selectIdByName(candidate) == null) {
                return candidate;
            }
        }
        throw new ServiceException(500, "副本命名冲突过多，请稍后重试");
    }

    @Override
    public String getSpaceName(Long id) {
        if (id == null) {
            return null;
        }
        KbSpaceDO space = spaceMapper.selectById(id);
        return space == null ? null : space.getName();
    }

    /** 当前登录用户 id（/knowledge-base/** 全部要求登录，调用侧必然已登录） */
    private Long currentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getId();
    }

    /** 归属校验：仅空间创建者可编辑 / 删除（ADR 0006：creator 为归属唯一真相源） */
    private void assertSpaceOwner(KbSpaceDO space) {
        Long userId = currentUserId();
        if (userId == null || !userId.equals(space.getCreator())) {
            throw exception(SPACE_NO_PERMISSION);
        }
    }

    private SpaceRespVO buildRespVO(KbSpaceDO space) {
        SpaceRespVO vo = new SpaceRespVO();
        vo.setId(space.getId());
        vo.setName(space.getName());
        vo.setDescription(space.getDescription());
        vo.setVisibility(space.getVisibility());
        vo.setCreateTime(space.getCreateTime());
        vo.setDocumentCount(spaceMapper.selectDocumentCount(space.getId()));
        return vo;
    }

}
