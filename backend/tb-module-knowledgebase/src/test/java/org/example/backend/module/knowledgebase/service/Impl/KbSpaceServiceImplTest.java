package org.example.backend.module.knowledgebase.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.model.vo.SpaceSaveReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库空间归属地基的 Service 接缝锁定（ADR 0006）。
 *
 * <p>锁两件事：列表按 creator 收口（账号 B 看不到账号 A 的空间），
 * 编辑 / 删除仅创建者本人可通过；新建一律写 creator + 私有可见性。
 *
 * <p>登录上下文用 {@code mockStatic(SecurityFrameworkUtils)} 注入，不启动 Spring、不碰数据库。
 */
@ExtendWith(MockitoExtension.class)
class KbSpaceServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_ID = 8L;
    private static final Long SPACE_ID = 100L;

    @Mock
    private KbSpaceMapper spaceMapper;
    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbDocumentChunkMapper chunkMapper;

    @InjectMocks
    private KbSpaceServiceImpl spaceService;

    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(SecurityFrameworkUtils.class);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private void loginAs(Long userId) {
        security.when(SecurityFrameworkUtils::getLoginUser)
                .thenReturn(new LoginUser().setId(userId));
    }

    private KbSpaceDO space(Long creator) {
        KbSpaceDO space = new KbSpaceDO();
        space.setId(SPACE_ID);
        space.setName("课程资料");
        space.setCreator(creator);
        space.setVisibility(0);
        return space;
    }

    private SpaceSaveReqVO saveReq() {
        SpaceSaveReqVO reqVO = new SpaceSaveReqVO();
        reqVO.setId(SPACE_ID);
        reqVO.setName("课程资料");
        reqVO.setDescription("课件与讲义");
        return reqVO;
    }

    @Test
    @DisplayName("列表：按当前登录用户过滤")
    void getSpaceList_passesCurrentUserAsFilter() {
        loginAs(USER_ID);
        when(spaceMapper.selectRespList(USER_ID)).thenReturn(List.of());

        spaceService.getSpaceList();

        verify(spaceMapper).selectRespList(USER_ID);
    }

    @Test
    @DisplayName("新建：写入创建者与私有可见性")
    void createSpace_persistsCreatorAndPrivateVisibility() {
        loginAs(USER_ID);
        doAnswer(invocation -> {
            invocation.getArgument(0, KbSpaceDO.class).setId(SPACE_ID);
            return 1;
        }).when(spaceMapper).insert(org.mockito.ArgumentMatchers.any(KbSpaceDO.class));

        Long id = spaceService.createSpace(saveReq());

        assertThat(id).isEqualTo(SPACE_ID);
        ArgumentCaptor<KbSpaceDO> captor = ArgumentCaptor.forClass(KbSpaceDO.class);
        verify(spaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreator()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getVisibility()).isZero();
    }

    @Test
    @DisplayName("更新：非创建者按模块错误码拒绝")
    void updateSpace_notOwner_throwsModuleErrorCode() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OTHER_ID));

        assertThatThrownBy(() -> spaceService.updateSpace(saveReq()))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));

        verify(spaceMapper, never()).updateById(org.mockito.ArgumentMatchers.any(KbSpaceDO.class));
    }

    @Test
    @DisplayName("更新：创建者本人可通过")
    void updateSpace_owner_updates() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(USER_ID));

        spaceService.updateSpace(saveReq());

        verify(spaceMapper).updateById(org.mockito.ArgumentMatchers.any(KbSpaceDO.class));
    }

    @Test
    @DisplayName("删除：非创建者按模块错误码拒绝，不触发级联")
    void deleteSpace_notOwner_throwsModuleErrorCode() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OTHER_ID));

        assertThatThrownBy(() -> spaceService.deleteSpace(SPACE_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));

        verify(documentMapper, never()).selectListBySpaceId(SPACE_ID);
        verify(spaceMapper, never()).deleteById(SPACE_ID);
    }

    @Test
    @DisplayName("删除：创建者本人触发级联删除")
    void deleteSpace_owner_cascades() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(USER_ID));
        when(documentMapper.selectListBySpaceId(SPACE_ID)).thenReturn(List.of(new KbDocumentDO()));

        spaceService.deleteSpace(SPACE_ID);

        verify(spaceMapper).deleteById(SPACE_ID);
    }

    @Test
    @DisplayName("可见性切换：创建者本人可通过，写库带上目标可见性")
    void updateSpaceVisibility_owner_updates() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(USER_ID));

        spaceService.updateSpaceVisibility(SPACE_ID, 1);

        verify(spaceMapper).updateVisibility(SPACE_ID, 1);
    }

    @Test
    @DisplayName("可见性切换：非创建者拒绝，即使空间已被设为公有")
    void updateSpaceVisibility_notOwner_throwsModuleErrorCode() {
        loginAs(USER_ID);
        KbSpaceDO publicSpace = space(OTHER_ID);
        publicSpace.setVisibility(1);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(publicSpace);

        assertThatThrownBy(() -> spaceService.updateSpaceVisibility(SPACE_ID, 0))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));

        verify(spaceMapper, never()).updateVisibility(any(), anyInt());
    }

    @Test
    @DisplayName("可见性切换：取值只能是 0/1，非法值不查库不写库")
    void updateSpaceVisibility_invalidValue_throwsModuleErrorCode() {
        loginAs(USER_ID);

        assertThatThrownBy(() -> spaceService.updateSpaceVisibility(SPACE_ID, 2))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_004));
        assertThatThrownBy(() -> spaceService.updateSpaceVisibility(SPACE_ID, null))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_004));

        verify(spaceMapper, never()).selectById(any());
        verify(spaceMapper, never()).updateVisibility(any(), anyInt());
    }

    @Test
    @DisplayName("可见性切换：空间不存在")
    void updateSpaceVisibility_spaceNotExists_throwsModuleErrorCode() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(null);

        assertThatThrownBy(() -> spaceService.updateSpaceVisibility(SPACE_ID, 1))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_000));
    }

    @Test
    @DisplayName("公有库列表：按当前登录用户排除自己的空间")
    void getPublicSpaceList_excludesCurrentUser() {
        loginAs(USER_ID);
        when(spaceMapper.selectPublicList(USER_ID)).thenReturn(List.of());

        spaceService.getPublicSpaceList();

        verify(spaceMapper).selectPublicList(USER_ID);
    }
}
