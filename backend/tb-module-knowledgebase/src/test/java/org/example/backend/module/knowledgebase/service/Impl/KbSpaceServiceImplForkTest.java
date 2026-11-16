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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库 fork「添加为自己的」的 Service 接缝锁定（US16）。
 *
 * <p>锁四件事：副本归属自己且私有、命名「原名（副本）」、复制链逐文档贯穿
 * （版面块 / 分片 / 向量都指向副本的新 id）、行数对账失败整笔回滚。
 * 事务用 stub 直通执行回调（不启动 Spring、不碰数据库），回滚语义由
 * 「异常从回调里原样抛出」体现——真库上由 TransactionTemplate 负责回滚。
 */
@ExtendWith(MockitoExtension.class)
class KbSpaceServiceImplForkTest {

    private static final Long MY_ID = 7L;
    private static final Long OWNER_ID = 8L;
    private static final Long SOURCE_SPACE_ID = 100L;
    private static final Long COPY_SPACE_ID = 200L;
    private static final Long SOURCE_DOC_ID = 11L;
    private static final Long COPY_DOC_ID = 300L;

    @Mock
    private KbSpaceMapper spaceMapper;
    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbDocumentParseBlockMapper parseBlockMapper;
    @Mock
    private KbDocumentChunkMapper chunkMapper;
    @Mock
    private TransactionTemplate transactionTemplate;

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

    /** 事务 stub：直接执行回调（真库上 TransactionTemplate 才有提交/回滚语义）。仅进入事务的用例才打桩 */
    private void stubTransactionPassthrough() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    }

    private KbSpaceDO publicSpaceOfOwner() {
        KbSpaceDO space = new KbSpaceDO();
        space.setId(SOURCE_SPACE_ID);
        space.setName("课程资料");
        space.setDescription("课件与讲义");
        space.setCreator(OWNER_ID);
        space.setVisibility(1);
        return space;
    }

    private KbDocumentDO completedDocument() {
        KbDocumentDO document = new KbDocumentDO();
        document.setId(SOURCE_DOC_ID);
        document.setSpaceId(SOURCE_SPACE_ID);
        document.setFileId(55L);
        document.setName("数据结构讲义.pdf");
        document.setType("PDF");
        document.setChunkCount(3);
        document.setStatus(2);
        return document;
    }

    /** fork 全链路的公共桩：副本空间 id=200、副本文档 id=300、复制行数与原库一致 */
    private void stubHappyCopyChain() {
        doAnswer(invocation -> {
            invocation.getArgument(0, KbSpaceDO.class).setId(COPY_SPACE_ID);
            return 1;
        }).when(spaceMapper).insert(any(KbSpaceDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, KbDocumentDO.class).setId(COPY_DOC_ID);
            return 1;
        }).when(documentMapper).insert(any(KbDocumentDO.class));
        when(spaceMapper.selectIdByName(any())).thenReturn(null);
        when(chunkMapper.copyChunks(anyLong(), anyLong(), anyLong())).thenReturn(3);
        when(chunkMapper.countVectorsByDocument(anyLong())).thenReturn(3L);
        when(chunkMapper.copyVectors(anyLong(), anyLong(), anyLong())).thenReturn(3);
    }

    @Test
    @DisplayName("fork 他人公有空间：副本归属自己、私有、命名「原名（副本）」，复制链指向新 id")
    void forkSpace_publicSpaceOfOther_createsIndependentCopy() {
        loginAs(MY_ID);
        when(spaceMapper.selectById(SOURCE_SPACE_ID)).thenReturn(publicSpaceOfOwner());
        when(documentMapper.selectCompletedListBySpaceId(SOURCE_SPACE_ID))
                .thenReturn(List.of(completedDocument()));
        stubTransactionPassthrough();
        stubHappyCopyChain();

        Long copyId = spaceService.forkSpace(SOURCE_SPACE_ID);

        assertThat(copyId).isEqualTo(COPY_SPACE_ID);

        ArgumentCaptor<KbSpaceDO> spaceCaptor = ArgumentCaptor.forClass(KbSpaceDO.class);
        verify(spaceMapper).insert(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().getName()).isEqualTo("课程资料（副本）");
        assertThat(spaceCaptor.getValue().getCreator()).isEqualTo(MY_ID);
        assertThat(spaceCaptor.getValue().getVisibility()).isZero();
        assertThat(spaceCaptor.getValue().getDescription()).isEqualTo("课件与讲义");

        ArgumentCaptor<KbDocumentDO> docCaptor = ArgumentCaptor.forClass(KbDocumentDO.class);
        verify(documentMapper).insert(docCaptor.capture());
        KbDocumentDO copyDoc = docCaptor.getValue();
        assertThat(copyDoc.getSpaceId()).isEqualTo(COPY_SPACE_ID);
        assertThat(copyDoc.getName()).isEqualTo("数据结构讲义.pdf");
        assertThat(copyDoc.getFileId()).isEqualTo(55L);
        assertThat(copyDoc.getStatus()).isEqualTo(2);
        assertThat(copyDoc.getChunkCount()).isEqualTo(3);

        verify(parseBlockMapper).copyBlocks(SOURCE_DOC_ID, COPY_DOC_ID, COPY_SPACE_ID);
        verify(chunkMapper).copyChunks(SOURCE_DOC_ID, COPY_DOC_ID, COPY_SPACE_ID);
        verify(chunkMapper).copyVectors(SOURCE_DOC_ID, COPY_DOC_ID, COPY_SPACE_ID);
    }

    @Test
    @DisplayName("fork 他人私有空间：拒绝，不起事务")
    void forkSpace_othersPrivateSpace_rejected() {
        loginAs(MY_ID);
        KbSpaceDO privateSpace = publicSpaceOfOwner();
        privateSpace.setVisibility(0);
        when(spaceMapper.selectById(SOURCE_SPACE_ID)).thenReturn(privateSpace);

        assertThatThrownBy(() -> spaceService.forkSpace(SOURCE_SPACE_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_003));

        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("fork：空间不存在")
    void forkSpace_spaceNotExists_throwsModuleErrorCode() {
        loginAs(MY_ID);
        when(spaceMapper.selectById(SOURCE_SPACE_ID)).thenReturn(null);

        assertThatThrownBy(() -> spaceService.forkSpace(SOURCE_SPACE_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_003_000_000));
    }

    @Test
    @DisplayName("fork：同名副本已存在时追加序号，避免撞唯一索引")
    void forkSpace_copyNameTaken_appendsSequence() {
        loginAs(MY_ID);
        when(spaceMapper.selectById(SOURCE_SPACE_ID)).thenReturn(publicSpaceOfOwner());
        when(documentMapper.selectCompletedListBySpaceId(SOURCE_SPACE_ID)).thenReturn(List.of());
        stubTransactionPassthrough();
        when(spaceMapper.selectIdByName("课程资料（副本）")).thenReturn(999L);
        when(spaceMapper.selectIdByName("课程资料（副本 2）")).thenReturn(null);

        spaceService.forkSpace(SOURCE_SPACE_ID);

        ArgumentCaptor<KbSpaceDO> spaceCaptor = ArgumentCaptor.forClass(KbSpaceDO.class);
        verify(spaceMapper).insert(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().getName()).isEqualTo("课程资料（副本 2）");
    }

    @Test
    @DisplayName("fork：分片复制不完整 → 整笔回滚（异常从事务回调原样抛出），不再复制向量")
    void forkSpace_chunkCopyIncomplete_rollsBack() {
        loginAs(MY_ID);
        when(spaceMapper.selectById(SOURCE_SPACE_ID)).thenReturn(publicSpaceOfOwner());
        when(documentMapper.selectCompletedListBySpaceId(SOURCE_SPACE_ID))
                .thenReturn(List.of(completedDocument()));
        stubTransactionPassthrough();
        doAnswer(invocation -> {
            invocation.getArgument(0, KbSpaceDO.class).setId(COPY_SPACE_ID);
            return 1;
        }).when(spaceMapper).insert(any(KbSpaceDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, KbDocumentDO.class).setId(COPY_DOC_ID);
            return 1;
        }).when(documentMapper).insert(any(KbDocumentDO.class));
        when(spaceMapper.selectIdByName(any())).thenReturn(null);
        when(chunkMapper.copyChunks(anyLong(), anyLong(), anyLong())).thenReturn(2);

        assertThatThrownBy(() -> spaceService.forkSpace(SOURCE_SPACE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("分片复制不完整");

        verify(chunkMapper, never()).copyVectors(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("fork：向量复制不完整（陈旧向量 join 不上）→ 整笔回滚")
    void forkSpace_vectorCopyIncomplete_rollsBack() {
        loginAs(MY_ID);
        when(spaceMapper.selectById(SOURCE_SPACE_ID)).thenReturn(publicSpaceOfOwner());
        when(documentMapper.selectCompletedListBySpaceId(SOURCE_SPACE_ID))
                .thenReturn(List.of(completedDocument()));
        stubTransactionPassthrough();
        doAnswer(invocation -> {
            invocation.getArgument(0, KbSpaceDO.class).setId(COPY_SPACE_ID);
            return 1;
        }).when(spaceMapper).insert(any(KbSpaceDO.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, KbDocumentDO.class).setId(COPY_DOC_ID);
            return 1;
        }).when(documentMapper).insert(any(KbDocumentDO.class));
        when(spaceMapper.selectIdByName(any())).thenReturn(null);
        when(chunkMapper.copyChunks(anyLong(), anyLong(), anyLong())).thenReturn(3);
        when(chunkMapper.countVectorsByDocument(anyLong())).thenReturn(3L);
        when(chunkMapper.copyVectors(anyLong(), anyLong(), anyLong())).thenReturn(1);

        assertThatThrownBy(() -> spaceService.forkSpace(SOURCE_SPACE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("向量复制不完整");
    }
}
