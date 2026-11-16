package org.example.backend.module.knowledgebase.service;

import org.example.backend.module.knowledgebase.enums.ErrorCodeConstants;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 知识库空间的访问口径（ADR 0006）：读写两把闸的唯一实现。
 *
 * <p>服务层在各自接缝调用（文档列表 / 分片列表走可读闸，上传 / 删除 / 向量化 / fork 落库走归属闸），
 * 口径只写这一份，避免多份 owner-or-public 判定各自漂移。
 */
public final class KbSpaceAccess {

    /** 可见性：公有（与 kb_space.visibility 的取值一致） */
    private static final int VISIBILITY_PUBLIC = 1;

    private KbSpaceAccess() {
    }

    /** 按主键加载空间，不存在按模块错误码拒绝 */
    public static KbSpaceDO requireExisting(KbSpaceMapper spaceMapper, Long spaceId) {
        KbSpaceDO space = spaceId == null ? null : spaceMapper.selectById(spaceId);
        if (space == null) {
            throw exception(ErrorCodeConstants.SPACE_NOT_EXISTS);
        }
        return space;
    }

    /** 公有只读闸：当前登录用户是创建者或空间已公开（US15），否则按模块错误码拒绝 */
    public static void requireReadable(KbSpaceDO space, Long userId) {
        boolean owner = userId != null && userId.equals(space.getCreator());
        boolean publicVisible = space.getVisibility() != null && space.getVisibility() == VISIBILITY_PUBLIC;
        if (!owner && !publicVisible) {
            throw exception(ErrorCodeConstants.SPACE_NO_PERMISSION);
        }
    }

    /** 归属写闸：空间必须存在且当前登录用户为其创建者，否则按模块错误码拒绝 */
    public static void requireOwned(KbSpaceDO space, Long userId) {
        if (userId == null || !userId.equals(space.getCreator())) {
            throw exception(ErrorCodeConstants.SPACE_NO_PERMISSION);
        }
    }

}
