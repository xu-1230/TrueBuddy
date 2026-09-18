package org.example.backend.module.knowledgebase.service.Impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.model.InfraFileDO;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.example.backend.module.knowledgebase.mapper.KbDocumentChunkMapper;
import org.example.backend.module.knowledgebase.mapper.KbDocumentMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentChunkDO;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.vo.DocumentRespVO;
import org.example.backend.module.knowledgebase.model.vo.DocumentSaveReqVO;
import org.example.backend.module.knowledgebase.service.KbDocumentService;
import org.example.backend.module.knowledgebase.service.parse.DocumentParseService;
import org.example.backend.module.knowledgebase.service.parse.TextCleaningService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.DOCUMENT_NOT_EXISTS;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.SPACE_NOT_EXISTS;

@Slf4j
@Service
public class KbDocumentServiceImpl implements KbDocumentService {

    /** 单文件最大 50MB */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    /** MinIO 存储目录 */
    private static final String UPLOAD_DIR = "knowledge-base";
    /** 向量库写入批大小 */
    private static final int VECTOR_BATCH_SIZE = 10;
    /** 单个分片最大字符数（超出则切分为子分片，约 3000 字） */
    private static final int MAX_CHUNK_CHARS = 3000;

    @Resource
    private KbDocumentMapper documentMapper;
    @Resource
    private KbDocumentChunkMapper chunkMapper;
    @Resource
    private KbSpaceMapper spaceMapper;
    @Resource
    private InfraFileMapper infraFileMapper;
    @Resource
    private MinioFileStorageService minioFileStorageService;
    @Resource
    private DocumentParseService documentParseService;
    @Resource
    private TextCleaningService textCleaningService;
    @Resource
    private VectorStore vectorStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<DocumentRespVO> getDocumentList(Long spaceId) {
        return documentMapper.selectListBySpaceId(spaceId).stream().map(this::buildRespVO).toList();
    }

    @Override
    public Long createDocument(DocumentSaveReqVO reqVO) {
        if (spaceMapper.selectById(reqVO.getSpaceId()) == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        KbDocumentDO document = new KbDocumentDO();
        document.setSpaceId(reqVO.getSpaceId());
        document.setName(reqVO.getName());
        document.setType(reqVO.getType());
        // 仅保存元数据，分片由后续解析/向量化流程写入
        document.setChunkCount(0);
        document.setStatus(0);
        documentMapper.insert(document);
        return document.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        if (documentMapper.selectById(id) == null) {
            throw exception(DOCUMENT_NOT_EXISTS);
        }
        // 级联逻辑删除：分片 -> 文档
        chunkMapper.deleteByDocumentId(id);
        documentMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> uploadAndPreview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(500, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException(500, "文件大小超过上限（50MB）");
        }

        String originalName = file.getOriginalFilename();
        String ext = getExt(originalName);
        long fileSize = file.getSize();

        log.info("预览解析文档: fileName={}, size={}", originalName, fileSize);

        // 1. 解析文件为 HTML -> 清洗 -> 按标签切分 -> 合并为分片
        String html = parseFileToHtml(file);
        String cleanedHtml = textCleaningService.cleanHtml(html);
        String strippedHtml = textCleaningService.stripHtml(cleanedHtml);
        List<String> tagList = TextCleaningService.splitHtmlByTag(strippedHtml);
        List<Document> chunks = textCleaningService.chunkByTagType(0, tagList);

        // 2. 切分超大分片，保证预览与最终落库一致
        List<Document> finalChunks = splitOversized(chunks);

        List<Map<String, Object>> chunkList = new ArrayList<>();
        for (int i = 0; i < finalChunks.size(); i++) {
            Document chunk = finalChunks.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("type", chunk.getMetadata().getOrDefault("tag_type", "p"));
            item.put("content", chunk.getText());
            chunkList.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", originalName);
        result.put("fileSize", fileSize);
        result.put("type", ext);
        result.put("totalChunks", finalChunks.size());
        result.put("chunks", chunkList);

        log.info("文档预览完成: fileName={}, chunks={}", originalName, finalChunks.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> vectorize(MultipartFile file, String chunksJson, Long spaceId) {
        if (spaceMapper.selectById(spaceId) == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException(500, "上传文件不能为空");
        }

        List<Map<String, Object>> chunkDataList;
        try {
            chunkDataList = objectMapper.readValue(chunksJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new ServiceException(500, "分片数据解析失败: " + e.getMessage());
        }

        String originalName = file.getOriginalFilename();
        String ext = getExt(originalName);
        long fileSize = file.getSize();
        int total = chunkDataList.size();
        log.info("向量化开始: fileName={}, chunks={}", originalName, total);

        // 1. 上传文件到 MinIO，并记录到 infra_file
        String objectName = minioFileStorageService.uploadFile(file, UPLOAD_DIR);
        String fileUrl = minioFileStorageService.getFileUrl(objectName);
        InfraFileDO infraFile = new InfraFileDO();
        infraFile.setBucket(minioFileStorageService.getBucket());
        infraFile.setPath(objectName);
        infraFile.setName(originalName);
        infraFile.setUrl(fileUrl);
        infraFile.setSize(fileSize);
        infraFile.setType(file.getContentType());
        infraFileMapper.insert(infraFile);

        // 2. 保存文档记录到 kb_document
        KbDocumentDO document = new KbDocumentDO();
        document.setSpaceId(spaceId);
        document.setFileId(infraFile.getId());
        document.setName(originalName);
        document.setType(ext.toUpperCase());
        document.setChunkCount(total);
        document.setStatus(2);
        documentMapper.insert(document);
        Long documentId = document.getId();

        // 3. 保存分片并构建向量文档
        List<Document> vectorDocs = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            Map<String, Object> item = chunkDataList.get(i);
            String content = String.valueOf(item.getOrDefault("content", ""));
            String tagType = String.valueOf(item.getOrDefault("type", "p"));
            String title = tagTitle(tagType);

            KbDocumentChunkDO chunk = new KbDocumentChunkDO();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i);
            chunk.setTitle(title);
            chunk.setContent(content);
            chunkMapper.insert(chunk);

            Document vectorDoc = new Document(content);
            vectorDoc.getMetadata().put("chunkId", String.valueOf(chunk.getId()));
            vectorDoc.getMetadata().put("documentId", String.valueOf(documentId));
            vectorDoc.getMetadata().put("spaceId", String.valueOf(spaceId));
            vectorDoc.getMetadata().put("title", title);
            vectorDocs.add(vectorDoc);
        }

        // 4. 分批写入向量库
        for (int i = 0; i < total; i += VECTOR_BATCH_SIZE) {
            int end = Math.min(i + VECTOR_BATCH_SIZE, total);
            vectorStore.add(vectorDocs.subList(i, end));
        }

        log.info("向量化完成: documentId={}, savedChunks={}", documentId, total);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("savedChunks", total);
        result.put("status", "completed");
        return result;
    }

    @Override
    public String searchBySpace(Long spaceId, String query, int topK) {
        if (spaceId == null || query == null || query.isBlank()) {
            return "";
        }
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(1, topK))
                    // 向量元数据中 spaceId 以字符串写入，此处按等值过滤
                    .filterExpression("spaceId == '" + spaceId + "'")
                    .build();
            List<Document> documents = vectorStore.similaritySearch(request);
            if (documents == null || documents.isEmpty()) {
                return "";
            }
            StringBuilder context = new StringBuilder();
            int index = 0;
            for (Document document : documents) {
                String text = document.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                context.append("【片段").append(++index).append("】")
                        .append(text)
                        .append("\n");
            }
            log.info("知识库检索完成: spaceId={}, topK={}, hits={}", spaceId, topK, index);
            return context.toString();
        } catch (Exception e) {
            log.warn("知识库检索失败（已忽略检索增强）: spaceId={}, error={}", spaceId, e.getMessage());
            return "";
        }
    }

    // ========== 私有方法 ==========

    /**
     * 将超过 MAX_CHUNK_CHARS 的分片切分为子分片：
     * - table 类型按 &lt;tr&gt; 行切分（保留表格结构）
     * - 其他类型按 \n 换行切分
     */
    private List<Document> splitOversized(List<Document> docs) {
        List<Document> result = new ArrayList<>();
        for (Document doc : docs) {
            String text = doc.getText();
            if (text.length() <= MAX_CHUNK_CHARS) {
                result.add(doc);
                continue;
            }
            String tagType = (String) doc.getMetadata().getOrDefault("tag_type", "p");
            if ("table".equals(tagType)) {
                result.addAll(splitTableDoc(doc, text));
            } else {
                result.addAll(splitByLines(doc, text));
            }
        }
        return result;
    }

    /** 按 tr 行切分 table 分片，超出行独立成子分片 */
    private List<Document> splitTableDoc(Document doc, String text) {
        List<Document> result = new ArrayList<>();
        String head = "";
        String tail = "";
        if (text.startsWith("<table")) {
            int closeTable = text.lastIndexOf("</table>");
            if (closeTable > 0) {
                head = text.substring(0, text.indexOf("<tr>"));
                tail = text.substring(closeTable);
                text = text.substring(head.length(), closeTable);
            }
        }

        String[] rows = text.split("(?=<tr>)");
        List<String> rowList = new ArrayList<>();
        for (String row : rows) {
            String trimmed = row.trim();
            if (!trimmed.isEmpty()) {
                rowList.add(trimmed);
            }
        }

        if (rowList.isEmpty()) {
            result.add(doc);
            return result;
        }

        String headerRow = rowList.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(head).append(headerRow);

        int i = 1;
        while (i < rowList.size()) {
            String row = rowList.get(i);
            if (sb.length() + row.length() > MAX_CHUNK_CHARS && sb.length() > head.length() + headerRow.length()) {
                sb.append(tail);
                result.add(buildSubDocument(doc, sb.toString()));
                sb = new StringBuilder();
                sb.append(head).append(headerRow);
            }
            sb.append(row);
            i++;
        }
        sb.append(tail);
        result.add(buildSubDocument(doc, sb.toString()));
        return result;
    }

    /** 按 \n 换行切分非 table 分片 */
    private List<Document> splitByLines(Document doc, String text) {
        List<Document> result = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() + line.length() > MAX_CHUNK_CHARS && sb.length() > 0) {
                result.add(buildSubDocument(doc, sb.toString()));
                sb.setLength(0);
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        if (sb.length() > 0) {
            result.add(buildSubDocument(doc, sb.toString()));
        }
        return result;
    }

    private Document buildSubDocument(Document source, String text) {
        Document sub = new Document(text);
        sub.getMetadata().putAll(source.getMetadata());
        return sub;
    }

    private String parseFileToHtml(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return documentParseService.parseContentHtml(inputStream);
        } catch (Exception e) {
            log.error("文件解析失败: {}", e.getMessage());
            throw new ServiceException(500, "文件内容提取失败");
        }
    }

    /** 分片类型 -> 中文标题 */
    private String tagTitle(String tagType) {
        if ("h".equals(tagType)) {
            return "标题";
        }
        if ("table".equals(tagType)) {
            return "表格";
        }
        return "正文";
    }

    private String getExt(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "unknown";
    }

    private DocumentRespVO buildRespVO(KbDocumentDO document) {
        DocumentRespVO vo = new DocumentRespVO();
        vo.setId(document.getId());
        vo.setSpaceId(document.getSpaceId());
        vo.setName(document.getName());
        vo.setType(document.getType());
        vo.setCreateTime(document.getCreateTime());
        return vo;
    }

}
