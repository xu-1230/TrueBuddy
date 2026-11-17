package org.example.backend.module.knowledgebase.service.Impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 检索增强单 / 多空间过滤的 Service 接缝锁定。
 *
 * <p>doctemplate 复用这两个方法：单空间保持 `spaceId == 'x'`，多空间使用
 * pgvector 支持的 `spaceId in ['x','y']`。这里不启动数据库，只锁构造出的
 * {@link SearchRequest} 与片段拼接口径。
 */
@ExtendWith(MockitoExtension.class)
class KbDocumentServiceImplSearchTest {

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private KbDocumentServiceImpl documentService;

    @Test
    @DisplayName("多空间检索：构造 IN 过滤并按顺序拼接片段")
    void searchBySpaces_buildsInFilterAndNumbersFragments() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("片段A"), new Document("片段B")));

        String context = documentService.searchBySpaces(List.of(11L, 22L, 11L), "查询", 5);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertThat(request.getTopK()).isEqualTo(5);
        assertThat(request.getFilterExpression()).isNotNull();
        assertThat(request.getFilterExpression().type()).isEqualTo(Filter.ExpressionType.IN);
        assertThat(request.getFilterExpression().toString()).contains("11").contains("22");
        assertThat(context).isEqualTo("【片段1】片段A\n【片段2】片段B\n");
    }

    @Test
    @DisplayName("单空间检索：保持等值过滤")
    void searchBySpace_keepsEqualityFilter() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(documentService.searchBySpace(11L, "查询", 5)).isEmpty();

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().type()).isEqualTo(Filter.ExpressionType.EQ);
    }

    @Test
    @DisplayName("空选择 / 空白查询：返回空串且不访问向量库")
    void searchBySpaces_emptyInputs_skipVectorStore() {
        assertThat(documentService.searchBySpaces(List.of(), "查询", 5)).isEmpty();
        assertThat(documentService.searchBySpaces(List.of(1L), "   ", 5)).isEmpty();
        assertThat(documentService.searchBySpaces(null, "查询", 5)).isEmpty();
        verifyNoInteractions(vectorStore);
    }
}
