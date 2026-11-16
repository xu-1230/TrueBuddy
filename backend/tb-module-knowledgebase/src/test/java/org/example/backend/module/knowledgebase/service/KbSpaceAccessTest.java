package org.example.backend.module.knowledgebase.service;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 知识库空间访问口径（{@link KbSpaceAccess}）的单元锁定。
 *
 * <p>读写两把闸在文档列表 / 分片列表 / fork / 各写路径被多处复用，口径只此一份：
 * 可读 = 创建者本人 或 已公开；归属写 = 仅创建者。这里直接锁判定本身。
 */
@ExtendWith(MockitoExtension.class)
class KbSpaceAccessTest {

    private static final Long SPACE_ID = 7L;
    private static final Long OWNER_ID = 9L;
    private static final Long OTHER_ID = 10L;

    @Mock
    private KbSpaceMapper spaceMapper;

    private KbSpaceDO space(Long creator, Integer visibility) {
        KbSpaceDO space = new KbSpaceDO();
        space.setId(SPACE_ID);
        space.setCreator(creator);
        space.setVisibility(visibility);
        return space;
    }

    @Test
    @DisplayName("可读闸：创建者本人的私有空间放行")
    void requireReadable_ownerPrivate_passes() {
        KbSpaceAccess.requireReadable(space(OWNER_ID, 0), OWNER_ID);
    }

    @Test
    @DisplayName("可读闸：他人的公有空间放行")
    void requireReadable_othersPublic_passes() {
        KbSpaceAccess.requireReadable(space(OWNER_ID, 1), OTHER_ID);
    }

    @Test
    @DisplayName("可读闸：他人的私有空间按模块错误码拒绝")
    void requireReadable_othersPrivate_rejected() {
        assertThatThrownBy(() -> KbSpaceAccess.requireReadable(space(OWNER_ID, 0), OTHER_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));
    }

    @Test
    @DisplayName("可读闸：匿名用户对私有空间同样拒绝（防御口径，正常链路已要求登录）")
    void requireReadable_anonymousPrivate_rejected() {
        assertThatThrownBy(() -> KbSpaceAccess.requireReadable(space(OWNER_ID, 0), null))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));
    }

    @Test
    @DisplayName("归属写闸：创建者放行，他人与匿名拒绝")
    void requireOwned_ownerOnly() {
        KbSpaceAccess.requireOwned(space(OWNER_ID, 0), OWNER_ID);

        assertThatThrownBy(() -> KbSpaceAccess.requireOwned(space(OWNER_ID, 1), OTHER_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));
        assertThatThrownBy(() -> KbSpaceAccess.requireOwned(space(OWNER_ID, 0), null))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));
    }

    @Test
    @DisplayName("存在性闸：不存在按模块错误码拒绝，存在则原样返回")
    void requireExisting_missing_rejected() {
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(null);

        assertThatThrownBy(() -> KbSpaceAccess.requireExisting(spaceMapper, SPACE_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_000));

        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OWNER_ID, 0));
        assertThat(KbSpaceAccess.requireExisting(spaceMapper, SPACE_ID).getCreator()).isEqualTo(OWNER_ID);
    }
}
