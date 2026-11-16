package org.example.backend.module.knowledgebase.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档链路空间归属守卫的 Service 接缝锁定（ADR 0006）。
 *
 * <p>知识库的写入口都以 spaceId / documentId 定位资源：文档操作通过 document.spaceId
 * 反查空间创建者，非本人一律按模块错误码 1_003_000_003 拒绝。
 * {@code searchBySpace} 是 doctemplate 检索增强复用的内部接缝，刻意不在本类覆盖。
 *
 * <p>登录上下文用 {@code mockStatic(SecurityFrameworkUtils)} 注入，不启动 Spring、不碰数据库。
 */
@ExtendWith(MockitoExtension.class)
class KbDocumentServiceImplOwnershipTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_ID = 8L;
    private static final Long SPACE_ID = 100L;
    private static final Long DOCUMENT_ID = 1L;

    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbSpaceMapper spaceMapper;

    @InjectMocks
    private KbDocumentServiceImpl documentService;

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
        space.setCreator(creator);
        return space;
    }

    private KbDocumentDO document(Long spaceId) {
        KbDocumentDO document = new KbDocumentDO();
        document.setId(DOCUMENT_ID);
        document.setSpaceId(spaceId);
        return document;
    }

    @Test
    @DisplayName("文档列表：他人空间按模块错误码拒绝")
    void getDocumentList_otherSpace_throwsModuleErrorCode() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OTHER_ID));

        assertThatThrownBy(() -> documentService.getDocumentList(SPACE_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));

        verify(documentMapper, never()).selectCompletedListBySpaceId(SPACE_ID);
    }

    @Test
    @DisplayName("文档列表：本人空间正常返回")
    void getDocumentList_ownSpace_returnsDocuments() {
        loginAs(USER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(USER_ID));
        when(documentMapper.selectCompletedListBySpaceId(SPACE_ID)).thenReturn(List.of());

        assertThat(documentService.getDocumentList(SPACE_ID)).isEmpty();
    }

    @Test
    @DisplayName("删除文档：他人空间下的文档按模块错误码拒绝")
    void deleteDocument_otherOwnersDocument_throwsModuleErrorCode() {
        loginAs(USER_ID);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(SPACE_ID));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OTHER_ID));

        assertThatThrownBy(() -> documentService.deleteDocument(DOCUMENT_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));
    }

    @Test
    @DisplayName("轮询解析状态：他人空间下的文档按模块错误码拒绝")
    void getParseStatus_otherOwnersDocument_throwsModuleErrorCode() {
        loginAs(USER_ID);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(SPACE_ID));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OTHER_ID));

        assertThatThrownBy(() -> documentService.getParseStatus(DOCUMENT_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));
    }
}
