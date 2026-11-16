package org.example.backend.module.knowledgebase.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentChunkDO;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.model.vo.ChunkRespVO;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分片列表的可读闸接缝锁定（US15 / ADR 0006）。
 *
 * <p>分片是文档正文的读取口径，与文档列表（{@code getDocumentList}）共用同一把闸：
 * 「创建者本人 或 公有空间」。私有空间的分片不得被任何其他登录用户凭 {@code documentId}
 * 枚举读走——公有库浏览区把他人文档 id 暴露给浏览者，这条路径必须锁死。
 *
 * <p>闸的具体判定（owner-or-public）收敛在 {@code KbSpaceAccess}，由其专属用例覆盖；
 * 本类只锁「分片接缝调用了这把闸」。
 */
@ExtendWith(MockitoExtension.class)
class KbDocumentChunkServiceImplTest {

    private static final Long SPACE_ID = 7L;
    private static final Long DOCUMENT_ID = 30L;
    private static final Long OWNER_ID = 9L;
    private static final Long OTHER_ID = 10L;

    @Mock
    private KbDocumentChunkMapper chunkMapper;
    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbSpaceMapper spaceMapper;

    @InjectMocks
    private KbDocumentChunkServiceImpl chunkService;

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

    private KbDocumentDO document(Long spaceId) {
        KbDocumentDO document = new KbDocumentDO();
        document.setId(DOCUMENT_ID);
        document.setSpaceId(spaceId);
        return document;
    }

    private KbSpaceDO space(Long creator, Integer visibility) {
        KbSpaceDO space = new KbSpaceDO();
        space.setId(SPACE_ID);
        space.setCreator(creator);
        space.setVisibility(visibility);
        return space;
    }

    private KbDocumentChunkDO chunk() {
        KbDocumentChunkDO chunk = new KbDocumentChunkDO();
        chunk.setId(300L);
        chunk.setDocumentId(DOCUMENT_ID);
        chunk.setChunkIndex(0);
        chunk.setContent("正文分片");
        return chunk;
    }

    @Test
    @DisplayName("分片列表：创建者本人可读自己私有空间的分片")
    void getChunkList_owner_privateSpaceReadable() {
        loginAs(OWNER_ID);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(SPACE_ID));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OWNER_ID, 0));
        when(chunkMapper.selectListByDocumentId(DOCUMENT_ID)).thenReturn(List.of(chunk()));

        List<ChunkRespVO> result = chunkService.getChunkList(DOCUMENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("正文分片");
    }

    @Test
    @DisplayName("分片列表：他人公有空间的分片可读（公有库只读浏览的取数路径）")
    void getChunkList_othersPublicSpaceReadable() {
        loginAs(OTHER_ID);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(SPACE_ID));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OWNER_ID, 1));
        when(chunkMapper.selectListByDocumentId(DOCUMENT_ID)).thenReturn(List.of());

        assertThat(chunkService.getChunkList(DOCUMENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("分片列表：他人私有空间按模块错误码拒绝，且不查分片")
    void getChunkList_othersPrivateSpaceRejected() {
        loginAs(OTHER_ID);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(SPACE_ID));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(OWNER_ID, 0));

        assertThatThrownBy(() -> chunkService.getChunkList(DOCUMENT_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));

        verify(chunkMapper, never()).selectListByDocumentId(anyLong());
    }

    @Test
    @DisplayName("分片列表：文档不存在按模块错误码拒绝")
    void getChunkList_documentNotExists_throwsModuleErrorCode() {
        loginAs(OTHER_ID);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> chunkService.getChunkList(DOCUMENT_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_001));

        verify(spaceMapper, never()).selectById(anyLong());
    }
}
