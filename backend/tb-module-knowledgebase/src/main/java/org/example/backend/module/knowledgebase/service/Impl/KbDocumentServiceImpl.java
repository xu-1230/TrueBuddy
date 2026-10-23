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
import org.example.backend.module.knowledgebase.mapper.KbDocumentParseBlockMapper;
import org.example.backend.module.knowledgebase.mapper.KbSpaceMapper;
import org.example.backend.module.knowledgebase.model.KbDocumentChunkDO;
import org.example.backend.module.knowledgebase.model.KbDocumentDO;
import org.example.backend.module.knowledgebase.model.KbDocumentParseBlockDO;
import org.example.backend.module.knowledgebase.model.vo.DocumentRespVO;
import org.example.backend.module.knowledgebase.model.vo.DocumentSaveReqVO;
import org.example.backend.module.knowledgebase.model.vo.LayoutVO;
import org.example.backend.module.knowledgebase.service.KbDocumentService;
import org.example.backend.module.knowledgebase.service.chunk.DocumentChunkService;
import org.example.backend.module.knowledgebase.service.parse.DocumentParseGateway;
import org.example.backend.module.knowledgebase.service.parse.ParseTaskStatus;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.DOCUMENT_ALREADY_COMPLETED;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.DOCUMENT_NOT_EXISTS;
import static org.example.backend.module.knowledgebase.enums.ErrorCodeConstants.SPACE_NOT_EXISTS;

/**
 * 知识库文档 Service 实现。
 *
 * <p>文档处理链路：上传（MinIO + 落库）→ 提交解析任务 → 轮询解析状态 → 解析产物落库（块级）
 * → AI 语义分片 → 分片落库 → 用户确认 → 向量化。
 * 解析引擎为 {@link DocumentParseGateway}（阿里云文档智能·文档解析大模型版），产出块级版面信息（layouts）；
 * 分片策略由 {@link DocumentChunkService} 决定。
 *
 * <p>状态机：0=待处理 → 1=解析中 → 5=解析完成待分片 → 6=AI分片中 → 4=分片待确认
 * →（向量化成功）2=已完成；任一步失败为 3=失败。
 * 「待确认」表示分片已落库但尚未向量化，此时用户放弃登记则执行逻辑删除（{@link #abandonDocument(Long)}）。
 *
 * <p>事务边界：本类不使用方法级 {@code @Transactional}。链路上的每一步都要调用解析引擎、大模型、
 * MinIO 或向量库，这些外部调用既不能回滚、也不该长时间持有数据库连接；改为用
 * {@link TransactionTemplate} 只把连续的数据库写入包成短事务，进度状态则单条自动提交、
 * 让并发轮询立刻可见。
 */
@Slf4j
@Service
public class KbDocumentServiceImpl implements KbDocumentService {

    /** 文档处理状态：待处理 */
    private static final int STATUS_PENDING = 0;
    /** 文档处理状态：解析中 */
    private static final int STATUS_PARSING = 1;
    /** 文档处理状态：已完成 */
    private static final int STATUS_COMPLETED = 2;
    /** 文档处理状态：失败 */
    private static final int STATUS_FAILED = 3;
    /** 文档处理状态：待确认（分片已落库，等待用户确认后向量化） */
    private static final int STATUS_PENDING_CONFIRM = 4;
    /** 文档处理状态：解析完成待分片（解析产物已落库，等待 AI 语义分片） */
    private static final int STATUS_PARSE_DONE = 5;
    /** 文档处理状态：AI 分片中 */
    private static final int STATUS_CHUNKING = 6;

    /** 单文件最大 50MB */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    /** MinIO 存储目录 */
    private static final String UPLOAD_DIR = "knowledge-base";
    /** 向量库写入批大小 */
    private static final int VECTOR_BATCH_SIZE = 10;

    @Resource
    private KbDocumentMapper documentMapper;
    @Resource
    private KbDocumentChunkMapper chunkMapper;
    @Resource
    private KbDocumentParseBlockMapper parseBlockMapper;
    @Resource
    private KbSpaceMapper spaceMapper;
    @Resource
    private InfraFileMapper infraFileMapper;
    @Resource
    private MinioFileStorageService minioFileStorageService;
    @Resource
    private DocumentParseGateway documentParseGateway;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private DocumentChunkService documentChunkService;

    @Resource
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 正在落块 / AI 分片的文档 id：前端会持续轮询，用于阻断并发重入（重复调用 LLM 会重复计费） */
    private final Set<Long> processingDocuments = ConcurrentHashMap.newKeySet();

    @Override
    public List<DocumentRespVO> getDocumentList(Long spaceId) {
        // 文档列表仅返回已完成向量化的文档；解析中 / 待确认 / 失败等中间态不对外展示
        return documentMapper.selectCompletedListBySpaceId(spaceId).stream().map(this::buildRespVO).toList();
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
        document.setStatus(STATUS_PENDING);
        document.setParseProgress(0);
        documentMapper.insert(document);
        return document.getId();
    }

    @Override
    public void deleteDocument(Long id) {
        KbDocumentDO document = documentMapper.selectById(id);
        if (document == null) {
            throw exception(DOCUMENT_NOT_EXISTS);
        }
        logicalDelete(document);
    }

    @Override
    public void abandonDocument(Long documentId) {
        KbDocumentDO document = documentMapper.selectById(documentId);
        if (document == null) {
            throw exception(DOCUMENT_NOT_EXISTS);
        }
        // 已完成向量化的文档视为最终入库结果，放弃流程不生效
        if (hasStatus(document, STATUS_COMPLETED)) {
            log.info("放弃文档流程被忽略（文档已完成向量化）: documentId={}", documentId);
            return;
        }
        log.info("放弃未确认文档，执行逻辑删除: documentId={}, status={}", documentId, document.getStatus());
        logicalDelete(document);
    }

    /**
     * 逻辑删除文档：分片 -> 文档（事务内）-> 向量库 -> 解析任务资源（事务外）。
     *
     * <p>向量清理与解析任务释放均为 best-effort，失败只记录日志，不影响删除结果；
     * 放在事务提交之后执行，避免外部调用拖住数据库连接。
     */
    private void logicalDelete(KbDocumentDO document) {
        Long documentId = document.getId();
        // 级联逻辑删除：分片 -> 文档（两步原子，避免「文档已删、分片还在」的孤儿行）
        transactionTemplate.executeWithoutResult(status -> {
            chunkMapper.deleteByDocumentId(documentId);
            documentMapper.deleteById(documentId);
        });
        // 清理向量库
        try {
            vectorStore.delete("documentId == '" + documentId + "'");
        } catch (Exception e) {
            log.warn("清理文档向量失败（已忽略）: documentId={}, error={}", documentId, e.getMessage());
        }
        // 释放解析引擎侧临时资源
        if (StringUtils.hasText(document.getDocmindJobId())) {
            try {
                documentParseGateway.release(document.getDocmindJobId());
            } catch (Exception e) {
                log.warn("释放解析任务失败（已忽略）: documentId={}, error={}", documentId, e.getMessage());
            }
        }
    }


    @Override
    public List<Map<String, Object>> uploadAndSubmit(List<MultipartFile> files, Long spaceId) {
        if (spaceId == null || spaceMapper.selectById(spaceId) == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        if (files == null || files.isEmpty()) {
            throw new ServiceException(500, "上传文件不能为空");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadSingle(file, spaceId));
        }
        log.info("文件列表上传提交解析完成: spaceId={}, total={}", spaceId, results.size());
        return results;
    }
    /** 上传单个文件并提交解析任务（MinIO 存储 + 落库 ） */
    private Map<String, Object> uploadSingle(MultipartFile file, Long spaceId) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(500, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException(500, "文件大小超过上限（50MB）");
        }

        String originalName = file.getOriginalFilename();
        String ext = getExt(originalName);
        long fileSize = file.getSize();
        if (!StringUtils.hasText(originalName)) {
            throw new ServiceException(500, "文件名称不能为空");
        }

        log.info("文档上传并提交解析: fileName={}, size={}", originalName, fileSize);

        // 1. 提交解析任务（云端为异步任务号；本地为内存任务号）
        String jobId;
        try (InputStream inputStream = file.getInputStream()) {
            jobId = documentParseGateway.submit(inputStream, originalName);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("提交解析任务失败: fileName={}, error={}", originalName, e.getMessage(), e);
            throw new ServiceException(500, "提交解析任务失败: " + e.getMessage());
        }

        // 2. 上传原文件到 MinIO（外部调用，留在事务外）
        String objectName = minioFileStorageService.uploadFile(file, UPLOAD_DIR);
        String fileUrl = minioFileStorageService.getFileUrl(objectName);
        InfraFileDO infraFile = new InfraFileDO();
        infraFile.setBucket(minioFileStorageService.getBucket());
        infraFile.setPath(objectName);
        infraFile.setName(originalName);
        infraFile.setUrl(fileUrl);
        infraFile.setSize(fileSize);
        infraFile.setType(file.getContentType());

        // 3. 落库：infra_file + kb_document 放进同一个短事务（fileId 依赖前者的自增主键）
        KbDocumentDO document = new KbDocumentDO();
        document.setSpaceId(spaceId);
        document.setName(originalName);
        document.setType(ext.toUpperCase());
        document.setChunkCount(0);
        document.setStatus(STATUS_PARSING);
        document.setDocmindJobId(jobId);
        document.setParseProgress(0);
        transactionTemplate.executeWithoutResult(status -> {
            infraFileMapper.insert(infraFile);
            document.setFileId(infraFile.getId());
            documentMapper.insert(document);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", document.getId());
        result.put("fileName", originalName);
        result.put("fileSize", fileSize);
        result.put("type", ext);
        result.put("status", STATUS_PARSING);
        log.info("文档已提交解析: documentId={}, jobId={}", document.getId(), jobId);
        return result;
    }

    /**
     * 轮询解析状态；解析成功时就地推进「落块 -> AI 分片 -> 分片落库」。
     *
     * <p>刻意不加 {@code @Transactional}：本方法会调用解析引擎与大模型，前端还在持续轮询，
     * 用事务包住外部调用会让每个轮询请求都长时间占住一个数据库连接。
     * 需要原子的多步写入由 {@link #handleParseSuccess} 内部按段各自开短事务。
     */
    @Override
    public Map<String, Object> getParseStatus(Long documentId) {
        KbDocumentDO document = documentMapper.selectById(documentId);
        if (document == null) {
            throw exception(DOCUMENT_NOT_EXISTS);
        }

        // 解析已完成（待确认 / 已完成）：无需轮询，直接返回落库分片
        if (hasStatus(document, STATUS_COMPLETED) || hasStatus(document, STATUS_PENDING_CONFIRM)) {
            return buildChunkResult(document, chunkMapper.selectListByDocumentId(documentId));
        }

        // 解析失败：返回失败原因
        if (hasStatus(document, STATUS_FAILED)) {
            return buildStateResult(document, document.getParseError());
        }

        // 正在落块 / AI 分片：直接返回当前状态，避免重复查询解析引擎与重复触发分片
        if (processingDocuments.contains(documentId)) {
            return buildStateResult(document, null);
        }

        String jobId = document.getDocmindJobId();
        if (!StringUtils.hasText(jobId)) {
            return buildStateResult(document, null);
        }

        ParseTaskStatus status;
        try {
            status = documentParseGateway.query(jobId);
        } catch (Exception e) {
            log.error("查询解析状态失败: documentId={}, jobId={}, error={}", documentId, jobId, e.getMessage(), e);
            // 不修改库中状态，交由前端提示并重试，避免瞬时故障导致文档永久失败
            return buildStateResult(document, "查询解析状态失败: " + e.getMessage());
        }

        return switch (status.getState()) {
            case FAILED -> {
                documentMapper.updateParseState(documentId, STATUS_FAILED, 0, status.getError());
                document.setStatus(STATUS_FAILED);
                document.setParseProgress(0);
                yield buildStateResult(document, status.getError());
            }
            case SUCCESS -> handleParseSuccess(document, jobId, status);
            default -> {
                documentMapper.updateParseState(documentId, STATUS_PARSING, status.getProgress(), null);
                document.setParseProgress(status.getProgress());
                yield buildStateResult(document, null);
            }
        };
    }

    /** 解析中/失败结果 */
    private Map<String, Object> buildStateResult(KbDocumentDO document, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", document.getId());
        result.put("fileName", document.getName());
        result.put("type", document.getType());
        result.put("status", document.getStatus());
        result.put("progress", document.getParseProgress() == null ? 0 : document.getParseProgress());
        if (StringUtils.hasText(error)) {
            result.put("error", error);
        }
        return result;
    }

    /** 解析完成结果：文档信息 + 分片列表（status 原样透传：2=已完成 / 4=待确认） */
    private Map<String, Object> buildChunkResult(KbDocumentDO document, List<?> chunks) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : chunks) {
            if (item instanceof Map<?, ?> map) {
                items.add(castChunkItem(map));
            } else if (item instanceof KbDocumentChunkDO row) {
                items.add(buildChunkItem(row.getChunkIndex(), row.getContent()));
            }
        }
        Map<String, Object> result = buildStateResult(document, null);
        result.put("totalChunks", items.size());
        result.put("chunks", items);
        return result;
    }

    /**
     * 解析成功：解析产物落库（块级）-> AI 语义分片 -> 分片落库 -> 置为待确认。
     *
     * <p>本方法含两次外部调用（拉取解析结果、调用大模型分片），耗时较长，所以不整体放进事务：
     * 只在两段连续的数据库写入上各开一个短事务，中间的大模型调用不持有连接。
     * 大模型调用失败时前面的落块结果保留（覆盖式写入，重试安全），文档停在「解析完成待分片」，
     * 下一轮轮询会重新触发本方法。
     *
     * <p>前端会持续轮询 {@link #getParseStatus(Long)}，故以 {@link #processingDocuments} 阻断并发重入。
     */
    private Map<String, Object> handleParseSuccess(KbDocumentDO document, String jobId, ParseTaskStatus status) {
        Long documentId = document.getId();
        Long spaceId = document.getSpaceId();
        if (!processingDocuments.add(documentId)) {
            log.info("文档正在分片处理中，跳过重复触发: documentId={}", documentId);
            return buildStateResult(document, null);
        }
        try {
            // 1. 拉取解析产物，过滤空块并按阅读顺序排序
            List<Map<?, ?>> layouts = documentParseGateway.fetchMarkdown(jobId);
            List<LayoutVO> layoutList = objectMapper.convertValue(layouts, new TypeReference<List<LayoutVO>>() {});
            List<LayoutVO> blocks = layoutList == null ? List.of() : collectBlocks(layoutList);
            if (blocks.isEmpty()) {
                return markParseFailed(document, "解析结果为空，未能提取到有效内容");
            }

            // 2. 解析产物落库（覆盖式），作为 AI 分片与后续溯源的唯一输入
            documentMapper.updateParseState(documentId, STATUS_PARSE_DONE, 100, null);
            transactionTemplate.executeWithoutResult(ignored -> saveParseBlocks(documentId, spaceId, blocks));

            // 3. AI 语义分片
            documentMapper.updateParseState(documentId, STATUS_CHUNKING, 100, null);
            List<String> chunkTexts = documentChunkService.chunk(
                    blocks.stream().map(layout -> contentOf(layout).strip()).toList());
            if (chunkTexts.isEmpty()) {
                return markParseFailed(document, "AI 分片未产出有效内容");
            }

            // 4. 覆盖式保存分片并置为「待确认」，等待用户确认后向量化
            List<Map<String, Object>> preview = transactionTemplate.execute(ignored -> {
                List<Map<String, Object>> saved = persistChunks(documentId, spaceId, chunkTexts);
                documentMapper.updateParseSuccess(documentId, chunkTexts.size());
                return saved;
            });
            releaseQuietly(jobId);
            log.info("文档解析与分片完成: documentId={}, blocks={}, chunks={}, pages={}, tables={}",
                    documentId, blocks.size(), chunkTexts.size(), status.getPageCount(), status.getTableCount());

            document.setStatus(STATUS_PENDING_CONFIRM);
            document.setChunkCount(chunkTexts.size());
            document.setParseProgress(100);
            return buildChunkResult(document, preview);
        } finally {
            processingDocuments.remove(documentId);
        }
    }

    /** 过滤空块并按阅读顺序（页码 + 页内序号）排序，得到待落库的版面块 */
    private List<LayoutVO> collectBlocks(List<LayoutVO> layouts) {
        return sortLayouts(layouts).stream()
                .filter(layout -> StringUtils.hasText(contentOf(layout)))
                .toList();
    }

    /** 覆盖式写入解析版面块：先物理删除旧块，再按阅读顺序批量落库 */
    private void saveParseBlocks(Long documentId, Long spaceId, List<LayoutVO> blocks) {
        parseBlockMapper.deleteByDocumentId(documentId);
        List<KbDocumentParseBlockDO> rows = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            LayoutVO layout = blocks.get(i);
            KbDocumentParseBlockDO row = new KbDocumentParseBlockDO();
            row.setDocumentId(documentId);
            row.setSpaceId(spaceId);
            row.setBlockIndex(i);
            row.setPageNum(layout.getPageNum());
            row.setPageBlockSeq(layout.getIndex());
            row.setBlockType(StringUtils.hasText(layout.getType()) ? layout.getType() : "text");
            row.setContent(contentOf(layout).strip());
            row.setText(layout.getText() == null ? null : layout.getText().strip());
            row.setExternalId(layout.getUniqueId());
            row.setTokenCount(0);
            rows.add(row);
        }
        parseBlockMapper.insertBatch(rows);
    }

    /** 阅读顺序：先按页码、再按页内序号排序（多页文档避免错序） */
    private List<LayoutVO> sortLayouts(List<LayoutVO> layouts) {
        List<LayoutVO> ordered = new ArrayList<>(layouts);
        ordered.sort(Comparator
                .comparingInt((LayoutVO layout) -> nullSafeInt(layout.getPageNum()))
                .thenComparingInt(layout -> nullSafeInt(layout.getIndex())));
        return ordered;
    }

    /** 版面块正文：优先 markdownContent，缺失时降级 text */
    private String contentOf(LayoutVO layout) {
        return StringUtils.hasText(layout.getMarkdownContent()) ? layout.getMarkdownContent() : layout.getText();
    }

    /** 释放解析引擎侧临时资源（best-effort，失败仅记录日志） */
    private void releaseQuietly(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return;
        }
        try {
            documentParseGateway.release(jobId);
        } catch (Exception e) {
            log.warn("释放解析任务失败（已忽略）: jobId={}, error={}", jobId, e.getMessage());
        }
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /** 标记解析失败并返回失败结果 */
    private Map<String, Object> markParseFailed(KbDocumentDO document, String error) {
        documentMapper.updateParseState(document.getId(), STATUS_FAILED, 0, error);
        document.setStatus(STATUS_FAILED);
        document.setParseProgress(0);
        return buildStateResult(document, error);
    }

    /**
     * 覆盖式写入用户确认后的分片，并按落库结果构建向量文档。
     *
     * <p>只在事务内调用：向量元数据里的 chunkId 来自分片行的自增主键，删旧与插新必须一起生效。
     */
    private List<Document> saveConfirmedChunks(Long documentId, Long docSpaceId,
                                               List<Map<String, Object>> confirmedChunks) {
        chunkMapper.deleteByDocumentId(documentId);
        List<Document> vectorDocs = new ArrayList<>(confirmedChunks.size());
        for (int i = 0; i < confirmedChunks.size(); i++) {
            String content = String.valueOf(confirmedChunks.get(i).getOrDefault("content", ""));

            KbDocumentChunkDO chunk = new KbDocumentChunkDO();
            chunk.setDocumentId(documentId);
            chunk.setSpaceId(docSpaceId);
            chunk.setChunkIndex(i);
            chunk.setContent(content);
            chunkMapper.insert(chunk);

            Document vectorDoc = new Document(content);
            vectorDoc.getMetadata().put("chunkId", String.valueOf(chunk.getId()));
            vectorDoc.getMetadata().put("documentId", String.valueOf(documentId));
            vectorDoc.getMetadata().put("spaceId", String.valueOf(docSpaceId));
            vectorDocs.add(vectorDoc);
        }
        return vectorDocs;
    }

    /** 覆盖式写入分片（AI 分片结果与前端确认结果共用） */
    private List<Map<String, Object>> persistChunks(Long documentId, Long spaceId, List<String> chunkTexts) {
        chunkMapper.deleteByDocumentId(documentId);
        List<Map<String, Object>> preview = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            String content = chunkTexts.get(i);
            KbDocumentChunkDO row = new KbDocumentChunkDO();
            row.setDocumentId(documentId);
            row.setSpaceId(spaceId);
            row.setChunkIndex(i);
            row.setContent(content);
            chunkMapper.insert(row);
            preview.add(buildChunkItem(i, content));
        }
        return preview;
    }

    /**
     * 用户确认分片后向量化：分片覆盖式落库（短事务）-> 向量库重建（事务外）-> 置为已完成。
     *
     * <p>向量库写入不能被事务包住：它不随数据库回滚。一旦半途失败又把分片事务一起回滚，
     * 向量库里就留下一批指向已消失分片的脏数据，而数据库认为这篇文档什么都没发生。
     * 改成先提交分片、再写向量；向量写失败时补偿清干净并原样抛出，
     * 文档停在「待确认」，用户重试即可（重试会再清一次旧向量）。
     */
    @Override
    public Map<String, Object> vectorize(Long documentId, Long spaceId, String chunksJson) {
        KbDocumentDO document = documentMapper.selectById(documentId);
        if (document == null) {
            throw exception(DOCUMENT_NOT_EXISTS);
        }
        if (spaceId == null || spaceMapper.selectById(spaceId) == null) {
            throw exception(SPACE_NOT_EXISTS);
        }
        // 已完成向量化的文档不可重复提交（分片与向量均以首次确认结果为准）
        if (hasStatus(document, STATUS_COMPLETED)) {
            log.warn("重复向量化被拦截: documentId={}", documentId);
            throw exception(DOCUMENT_ALREADY_COMPLETED);
        }

        List<Map<String, Object>> chunkDataList;
        try {
            chunkDataList = objectMapper.readValue(chunksJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new ServiceException(500, "分片数据解析失败: " + e.getMessage());
        }
        if (chunkDataList == null || chunkDataList.isEmpty()) {
            throw new ServiceException(500, "分片列表不能为空");
        }

        // 过滤空白分片（用户编辑时可能新增空白段），并按确认后的顺序重新编号
        List<Map<String, Object>> confirmedChunks = new ArrayList<>();
        for (Map<String, Object> item : chunkDataList) {
            if (StringUtils.hasText(String.valueOf(item.getOrDefault("content", "")))) {
                confirmedChunks.add(item);
            }
        }
        if (confirmedChunks.isEmpty()) {
            throw new ServiceException(500, "有效分片不能为空");
        }

        int total = confirmedChunks.size();
        // space_id 一律以文档所属空间为准，避免与 kb_document.space_id 不一致（跨空间检索泄漏）
        Long docSpaceId = document.getSpaceId();
        log.info("向量化开始: documentId={}, chunks={}, spaceId={}", documentId, total, docSpaceId);

        // 1. 覆盖式保存分片并构建向量文档：删旧 + 插新必须原子，向量元数据还要回读分片自增 id
        List<Document> vectorDocs = transactionTemplate.execute(ignored ->
                saveConfirmedChunks(documentId, docSpaceId, confirmedChunks));

        // 2. 清理该文档旧向量，避免重复向量化产生脏数据
        try {
            vectorStore.delete("documentId == '" + documentId + "'");
        } catch (Exception e) {
            log.warn("清理旧向量失败（已忽略）: documentId={}, error={}", documentId, e.getMessage());
        }

        // 3. 分批写入向量库（外部调用，事务外）
        try {
            for (int i = 0; i < total; i += VECTOR_BATCH_SIZE) {
                int end = Math.min(i + VECTOR_BATCH_SIZE, total);
                vectorStore.add(vectorDocs.subList(i, end));
            }
        } catch (Exception e) {
            // 半途已写入的向量不清就会成为永久脏数据（分片行还在，但文档没被置为已完成）
            log.error("向量化写入失败，清理半成品向量: documentId={}, error={}", documentId, e.getMessage(), e);
            try {
                vectorStore.delete("documentId == '" + documentId + "'");
            } catch (Exception cleanupError) {
                log.warn("清理半成品向量失败（已忽略）: documentId={}, error={}", documentId, cleanupError.getMessage());
            }
            throw e;
        }

        // 4. 向量库写入成功后才把文档置为「已完成」
        documentMapper.updateChunkCount(documentId, total);
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

    private boolean hasStatus(KbDocumentDO document, int status) {
        return document.getStatus() != null && document.getStatus() == status;
    }

    private Map<String, Object> buildChunkItem(int index, String content) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("content", content);
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castChunkItem(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
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
        vo.setStatus(document.getStatus());
        vo.setParseProgress(document.getParseProgress());
        vo.setParseError(document.getParseError());
        vo.setCreateTime(document.getCreateTime());
        return vo;
    }

}
