package org.example.backend.module.knowledgebase.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentParseBlockMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.model.vo.DocumentRespVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公有空间只读读取的守卫口径（US15）。
 *
 * <p>读路径（文档列表）放行「创建者本人 或 公有空间」；私有空间对非创建者保持拒绝——
 * 公有 ≠ 全开放，分片等读取链路的存在性校验在各自接缝，这里只锁文档列表的口径。
 * 写路径（上传 / 删除 / 向量化）不在本类覆盖，它们仍走 {@code requireOwnedSpace}。
 */
@ExtendWith(MockitoExtension.class)
class KbDocumentServiceImplReadAccessTest {

    private static final Long SPACE_ID = 7L;
    private static final Long OWNER_ID = 9L;
    private static final Long OTHER_ID = 10L;

    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbDocumentChunkMapper chunkMapper;
    @Mock
    private KbDocumentParseBlockMapper parseBlockMapper;
    @Mock
    private KbSpaceMapper spaceMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private VectorStore vectorStore;

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

    private KbSpaceDO space(Long creator, Integer visibility) {
        KbSpaceDO space = new KbSpaceDO();
        space.setId(SPACE_ID);
        space.setCreator(creator);
        space.setVisibility(visibility);
        return space;
    }

    @Test
    @DisplayName("文档列表：创建者本人可读自己的私有空间")
    void getDocumentList_owner_privateSpaceReadable() {
        loginAs(OWNER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OWNER_ID, 0));
        when(documentMapper.selectCompletedListBySpaceId(SPACE_ID)).thenReturn(List.of(new KbDocumentDO()));

        List<DocumentRespVO> result = documentService.getDocumentList(SPACE_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("文档列表：他人公有空间可读（只读浏览的入口）")
    void getDocumentList_othersPublicSpaceReadable() {
        loginAs(OTHER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OWNER_ID, 1));
        when(documentMapper.selectCompletedListBySpaceId(SPACE_ID)).thenReturn(List.of());

        List<DocumentRespVO> result = documentService.getDocumentList(SPACE_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("文档列表：他人私有空间拒绝，且不查询文档")
    void getDocumentList_othersPrivateSpaceRejected() {
        loginAs(OTHER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OWNER_ID, 0));

        assertThatThrownBy(() -> documentService.getDocumentList(SPACE_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));

        verify(documentMapper, never()).selectCompletedListBySpaceId(anyLong());
    }

    @Test
    @DisplayName("文档列表：空间不存在")
    void getDocumentList_spaceNotExists_throwsModuleErrorCode() {
        loginAs(OTHER_ID);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(null);

        assertThatThrownBy(() -> documentService.getDocumentList(SPACE_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_000));
    }
}
