package org.example.backend.module.knowledgebase.service.Impl;

import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentParseBlockMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbSpaceDO;
import org.example.backend.module.knowledgebase.service.chunk.DocumentChunkService;
import org.example.backend.module.knowledgebase.service.parse.DocumentParseGateway;
import org.example.backend.module.knowledgebase.service.parse.ParseTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档链路的「事务里不包外部调用」回归锁定（Service 接缝）。
 *
 * <p>为什么锁在这一层：解析引擎、大模型、向量库都不随数据库回滚。把它们圈进事务，
 * 一半是连接被长时间占住（前端还在持续轮询），一半是「数据库回滚了、外部却已经生效」的
 * 双向不一致——这两种故障在现场都表现为「文档卡在中间态」，排查成本极高。
 *
 * <p>怎么锁：把真实 {@link TransactionTemplate} 架在 mock 的事务管理器上，于是
 * commit 变成可观测的事件，能够直接断言外部调用发生在提交之后。
 * 这比断言「方法上没有 {@code @Transactional}」要结实得多——后者改一行注解就能骗过去。
 *
 * <p>与会议模块的 MeetingServiceImplTest 一样，不启动 Spring 上下文、不碰数据库。
 */
@ExtendWith(MockitoExtension.class)
class KbDocumentServiceImplTransactionBoundaryTest {

    private static final Long DOCUMENT_ID = 1L;
    private static final Long SPACE_ID = 7L;

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbDocumentChunkMapper chunkMapper;
    @Mock
    private KbDocumentParseBlockMapper parseBlockMapper;
    @Mock
    private KbSpaceMapper spaceMapper;
    @Mock
    private InfraFileMapper infraFileMapper;
    @Mock
    private MinioFileStorageService minioFileStorageService;
    @Mock
    private DocumentParseGateway documentParseGateway;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private DocumentChunkService documentChunkService;

    @InjectMocks
    private KbDocumentServiceImpl documentService;

    /** 一篇「分片待确认」（status=4）的文档，向量化入口用的就是它 */
    private KbDocumentDO pendingConfirmDocument() {
        KbDocumentDO document = new KbDocumentDO();
        document.setId(DOCUMENT_ID);
        document.setSpaceId(SPACE_ID);
        document.setStatus(4);
        return document;
    }

    private KbDocumentDO parsingDocument() {
        KbDocumentDO document = pendingConfirmDocument();
        document.setStatus(1);
        document.setDocmindJobId("job-1");
        return document;
    }

    @Test
    @DisplayName("向量化：分片先提交，之后才写向量库")
    void vectorWriteHappensAfterChunksAreCommitted() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(pendingConfirmDocument());
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(new KbSpaceDO());

        documentService.vectorize(DOCUMENT_ID, SPACE_ID, "[{\"content\":\"第一段\"},{\"content\":\"第二段\"}]");

        InOrder order = inOrder(chunkMapper, transactionManager, vectorStore, documentMapper);
        order.verify(chunkMapper).deleteByDocumentId(DOCUMENT_ID);
        // 分片事务先提交，向量库才可能读到真实存在的分片行
        order.verify(transactionManager).commit(isNull());
        order.verify(vectorStore).add(anyList());
        order.verify(documentMapper).updateChunkCount(DOCUMENT_ID, 2);
    }

    @Test
    @DisplayName("向量库写失败：清掉半成品向量，且不把文档判成已完成")
    void failedVectorWriteIsCompensated() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(pendingConfirmDocument());
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(new KbSpaceDO());
        doThrow(new IllegalStateException("向量库不可用")).when(vectorStore).add(anyList());

        assertThatThrownBy(() -> documentService.vectorize(DOCUMENT_ID, SPACE_ID, "[{\"content\":\"第一段\"}]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("向量库不可用");

        // 一次是写入前的例行清理，一次是失败后的补偿清理
        verify(vectorStore, times(2)).delete("documentId == '" + DOCUMENT_ID + "'");
        verify(documentMapper, never()).updateChunkCount(any(), anyInt());
    }

    @Test
    @DisplayName("AI 分片跑在落块事务提交之后，不被事务圈住")
    void chunkingRunsAfterTheBlockTransactionCommits() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(parsingDocument());
        when(documentParseGateway.query("job-1")).thenReturn(ParseTaskStatus.success());
        List<Map<?, ?>> layouts = List.of(Map.<String, Object>of("text", "正文一段"));
        when(documentParseGateway.fetchMarkdown("job-1")).thenReturn(layouts);
        when(documentChunkService.chunk(anyList())).thenReturn(List.of("分片一"));

        documentService.getParseStatus(DOCUMENT_ID);

        InOrder order = inOrder(transactionManager, documentChunkService, parseBlockMapper);
        order.verify(parseBlockMapper).insertBatch(anyList());
        // 解析产物落库的事务先提交，然后才调用大模型
        order.verify(transactionManager).commit(isNull());
        order.verify(documentChunkService).chunk(anyList());
    }
}
