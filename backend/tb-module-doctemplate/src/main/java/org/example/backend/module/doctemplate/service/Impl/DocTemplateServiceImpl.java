package org.example.backend.module.doctemplate.service.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.doctemplate.config.DocTemplateProperties;
import org.example.backend.module.doctemplate.mapper.DocTemplateFileMapper;
import org.example.backend.module.doctemplate.mapper.DocTemplateGenerationMapper;
import org.example.backend.module.doctemplate.mapper.DocTemplateVersionMapper;
import org.example.backend.module.doctemplate.model.DocTemplateFileDO;
import org.example.backend.module.doctemplate.model.DocTemplateGenerationDO;
import org.example.backend.module.doctemplate.model.DocTemplateVersionDO;
import org.example.backend.module.doctemplate.model.vo.DocFileRespVO;
import org.example.backend.module.doctemplate.model.vo.DocPlanResult;
import org.example.backend.module.doctemplate.model.vo.DocxReplaceReport;
import org.example.backend.module.doctemplate.model.vo.DocxValidateResult;
import org.example.backend.module.doctemplate.model.vo.GenerateReqVO;
import org.example.backend.module.doctemplate.model.vo.GenerationHistoryRespVO;
import org.example.backend.module.doctemplate.model.vo.GenerationRespVO;
import org.example.backend.module.doctemplate.model.vo.ReplacementRule;
import org.example.backend.module.doctemplate.service.AiContentService;
import org.example.backend.module.doctemplate.service.DocTemplateService;
import org.example.backend.module.doctemplate.service.DocxMcpGateway;
import org.example.backend.module.doctemplate.service.TemplateDigestBuilder;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.model.InfraFileDO;
import org.example.backend.module.infra.model.vo.FileUploadRespVO;
import org.example.backend.module.infra.service.FileService;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.example.backend.module.knowledgebase.service.KbDocumentService;
import org.example.backend.module.knowledgebase.service.KbSpaceService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.DESCRIPTION_REQUIRED;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.DESCRIPTION_TOO_LONG;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.GENERATION_NOT_EXISTS;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.TEMPLATE_FILE_ID_REQUIRED;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.TEMPLATE_NOT_EXISTS;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.TEMPLATE_TYPE_UNSUPPORTED;

/**
 * 文档模板生成 Service 实现。
 * <p>
 * 编排链路：解析模板结构（骨架 / 表格与嵌套表格段落清单 / 正文段落清单）→ LLM 规划份数与主题定位 →
 * 逐份 LLM 产出「按位置区分」的 old→new 规则 → docx_replace(dryRun) 预演并按位置展开 →
 * docx_replace 落盘（只改 w:t，版式零变化）→ docx_fill_fields 填主题封面字段 →
 * docx_validate 自检（缺口则补规则重落盘，有限轮次）→ MinIO 上传 + infra_file 落库 → 生成记录落库。
 */
@Slf4j
@Service
public class DocTemplateServiceImpl implements DocTemplateService {

    /** docx 内容类型 */
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    /** 模板文档支持扩展名 */
    private static final String DOCX_EXT = ".docx";
    /** 检索增强返回分片数 */
    private static final int RAG_TOP_K = 5;
    /** 历史记录默认返回条数 */
    private static final int HISTORY_DEFAULT_LIMIT = 20;
    /** 历史记录最大返回条数 */
    private static final int HISTORY_MAX_LIMIT = 100;
    /** 描述说明最大长度 */
    private static final int DESCRIPTION_MAX = 500;
    /** 使用内置默认知识库时的空间 id 字符串（前端约定） */
    private static final String DEFAULT_SPACE_ID = "default";
    /** docx_get_text 每页块数 */
    private static final int TEXT_PAGE_SIZE = 200;
    /** docx_get_text 翻页上限（防止异常返回导致死循环） */
    private static final int TEXT_MAX_PAGES = 30;
    /** 自检必备关键词兜底：文档中存在的结构锚点（保证改写后骨架未丢） */
    private static final List<String> STRUCTURE_ANCHORS = List.of(
            "教学目标", "学情分析", "重点和难点", "板书设计", "教学反思",
            "教学内容", "教学方法", "教学过程", "教材解读");
    /** 自检关键词上限（必检 / 禁检） */
    private static final int MAX_CHECK_KEYWORDS = 8;
    /** 关键词最长长度（需容纳「第3章 关系数据库标准语言SQL」这类精准旧主题词） */
    private static final int MAX_KEYWORD_LENGTH = 40;
    /** 封面字段标签（仅填用户明确给出的主题字段） */
    private static final String COVER_COURSE_NAME = "课程名称";
    private static final String COVER_CHAPTER_NAME = "章节名称";
    private static final String COVER_MAJOR = "面向专业";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private DocTemplateGenerationMapper generationMapper;
    @Resource
    private DocTemplateFileMapper fileMapper;
    @Resource
    private DocTemplateVersionMapper versionMapper;
    @Resource
    private InfraFileMapper infraFileMapper;
    @Resource
    private MinioFileStorageService minioFileStorageService;
    @Resource
    private FileService fileService;
    @Resource
    private KbDocumentService kbDocumentService;
    @Resource
    private KbSpaceService kbSpaceService;
    @Resource
    private DocxMcpGateway docxMcpGateway;
    @Resource
    private TemplateDigestBuilder digestBuilder;
    @Resource
    private AiContentService aiContentService;
    @Resource
    private DocTemplateProperties properties;

    /** 当前登录用户：生成 / 历史 / 详情 / 编辑都按 creator 隔离 */
    private Long currentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        }
        return loginUser.getId();
    }

    @Override
    public GenerationRespVO generate(GenerateReqVO reqVO) {
        Long userId = currentUserId();
        // 1. 参数校验
        validateRequest(reqVO);

        // 2. 校验模板文件（存在性 + 类型）
        InfraFileDO templateFile = infraFileMapper.selectById(reqVO.getTemplateFileId());
        if (templateFile == null) {
            throw exception(TEMPLATE_NOT_EXISTS);
        }
        if (templateFile.getName() == null
                || !templateFile.getName().toLowerCase().endsWith(DOCX_EXT)) {
            throw exception(TEMPLATE_TYPE_UNSUPPORTED);
        }

        // 3. 落「生成中」记录，返回任务 id
        Long spaceId = parseSpaceId(reqVO.getSpaceId());
        DocTemplateGenerationDO generation = new DocTemplateGenerationDO();
        generation.setTemplateFileId(reqVO.getTemplateFileId());
        generation.setSpaceId(spaceId);
        generation.setDescription(reqVO.getDescription());
        generation.setQuality(reqVO.getQuality());
        generation.setDocCount(0);
        generation.setStatus(0);
        generation.setCreator(userId);
        generation.setUpdater(userId);
        generationMapper.insert(generation);
        Long generationId = generation.getId();

        long startedAt = System.currentTimeMillis();
        Path workDir = null;
        try {
            // 4. 准备临时目录与模板可访问地址
            workDir = Path.of(properties.getTempDir(), String.valueOf(generationId));
            Files.createDirectories(workDir);
            String templateUrl = minioFileStorageService.getFileUrl(templateFile.getPath());

            // 5. 解析模板结构：骨架 + 表格/嵌套表格段落清单 + 正文段落清单（翻页取全）+ 体检基线
            String outline = docxMcpGateway.openOutline(templateUrl);
            String tablesDigest = digestBuilder.digestTables(docxMcpGateway.getTables(templateUrl));
            String textDigest = fetchAllText(templateUrl);
            DocxValidateResult templateBaseline = validateQuietly(templateUrl, null, null);
            log.info("模板解析完成: generationId={}, 表格清单={}字符, 正文清单={}字符, 基线结构={}",
                    generationId, tablesDigest.length(), textDigest.length(),
                    templateBaseline == null ? null : templateBaseline.getStructure());

            // 6. 知识库检索增强（未选择或选择默认知识库时为空）
            String ragContext = kbDocumentService.searchBySpace(spaceId, reqVO.getDescription(), RAG_TOP_K);

            // 7. LLM 规划：份数 + 每份主题定位与自检关键词
            DocPlanResult plan = aiContentService.plan(reqVO.getDescription(), outline, tablesDigest,
                    textDigest, ragContext, properties.getMaxDocCount());
            log.info("生成规划完成: generationId={}, docs={}, themes={}", generationId, plan.getDocs().size(),
                    plan.getDocs().stream().map(DocPlanResult.DocItem::getTheme).collect(Collectors.toList()));

            // 8. 逐份改写并生成 docx（单份失败不影响其余）
            List<DocFileRespVO> docs = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int index = 0;
            for (DocPlanResult.DocItem item : plan.getDocs()) {
                index++;
                try {
                    OneResult one = generateOne(generationId, index, item, textDigest, tablesDigest,
                            templateUrl, workDir, ragContext, templateBaseline, userId);
                    docs.add(one.doc());
                    errors.addAll(one.warnings());
                } catch (Exception e) {
                    String message = "第 " + index + " 份（" + item.getTag() + "）生成失败：" + e.getMessage();
                    log.error("[generate] {}", message, e);
                    errors.add(message);
                }
            }

            // 9. 回填任务结果
            Integer status = docs.isEmpty() ? 3 : (errors.isEmpty() ? 1 : 2);
            long duration = System.currentTimeMillis() - startedAt;
            DocTemplateGenerationDO update = new DocTemplateGenerationDO();
            update.setId(generationId);
            update.setStatus(status);
            update.setDocCount(docs.size());
            update.setErrorMsg(joinErrors(errors));
            update.setDurationMs(duration);
            generationMapper.updateResult(update);

            GenerationRespVO respVO = new GenerationRespVO();
            respVO.setGenerationId(generationId);
            respVO.setStatus(status);
            respVO.setDurationMs(duration);
            respVO.setErrorMsg(joinErrors(errors));
            respVO.setDocs(docs);
            log.info("文档生成完成: generationId={}, status={}, docs={}, cost={}ms",
                    generationId, status, docs.size(), duration);
            return respVO;
        } catch (ServiceException e) {
            markFailed(generationId, startedAt, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[generate] 生成失败: generationId={}", generationId, e);
            markFailed(generationId, startedAt, e.getMessage());
            return failedResult(generationId, startedAt, e.getMessage());
        } finally {
            cleanUp(workDir);
        }
    }

    @Override
    public List<GenerationHistoryRespVO> getHistory(int limit) {
        Long userId = currentUserId();
        int size = limit <= 0 ? HISTORY_DEFAULT_LIMIT : Math.min(limit, HISTORY_MAX_LIMIT);
        List<DocTemplateGenerationDO> generations = generationMapper.selectList(size, userId);
        Map<Long, List<DocTemplateFileDO>> filesByGeneration = loadFilesByGeneration(generations);
        List<GenerationHistoryRespVO> result = new ArrayList<>();
        for (DocTemplateGenerationDO generation : generations) {
            result.add(buildHistoryVO(generation, filesByGeneration.get(generation.getId())));
        }
        return result;
    }

    /**
     * 一次批量取出本次历史涉及的所有产出文件，按 generationId 分组。
     * 首页「文档」tab 依赖历史记录内联的 docs 展开文件卡，不能逐任务查（N+1）。
     */
    private Map<Long, List<DocTemplateFileDO>> loadFilesByGeneration(List<DocTemplateGenerationDO> generations) {
        if (generations == null || generations.isEmpty()) {
            return Map.of();
        }
        List<Long> generationIds = generations.stream()
                .map(DocTemplateGenerationDO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (generationIds.isEmpty()) {
            return Map.of();
        }
        return fileMapper.selectListByGenerationIds(generationIds).stream()
                .collect(Collectors.groupingBy(DocTemplateFileDO::getGenerationId));
    }

    @Override
    public GenerationRespVO getDetail(Long id) {
        Long userId = currentUserId();
        DocTemplateGenerationDO generation = generationMapper.selectById(id);
        // 生成记录按 creator 隔离：他人的记录一律按不存在处理，避免枚举
        if (generation == null || !Objects.equals(generation.getCreator(), userId)) {
            throw exception(GENERATION_NOT_EXISTS);
        }
        List<DocFileRespVO> docs = new ArrayList<>();
        for (DocTemplateFileDO file : fileMapper.selectListByGenerationId(id)) {
            docs.add(buildDocVO(file));
        }
        GenerationRespVO respVO = new GenerationRespVO();
        respVO.setGenerationId(generation.getId());
        respVO.setStatus(generation.getStatus());
        respVO.setDurationMs(generation.getDurationMs());
        respVO.setErrorMsg(generation.getErrorMsg());
        respVO.setDocs(docs);
        return respVO;
    }

    // ========== 私有方法：模板解析 ==========

    /** 翻页取全部正文段落清单（避免硬编码 page=1 导致长文档丢内容） */
    private String fetchAllText(String source) {
        StringBuilder builder = new StringBuilder();
        int pages = 1;
        boolean completed = false;
        for (int page = 1; page <= TEXT_MAX_PAGES; page++) {
            String raw = docxMcpGateway.getText(source, page, TEXT_PAGE_SIZE);
            builder.append(digestBuilder.digestTextBlocks(raw));
            pages = digestBuilder.pages(raw);
            if (page >= pages) {
                completed = true;
                break;
            }
        }
        if (!completed && pages > TEXT_MAX_PAGES) {
            log.warn("[fetchAllText] 模板共 {} 页，仅取前 {} 页（如内容较多请调大 TEXT_MAX_PAGES）", pages, TEXT_MAX_PAGES);
        }
        return builder.toString();
    }

    // ========== 私有方法：单份生成 ==========

    /** 单份生成结果：文件 VO + 自检告警 */
    private record OneResult(DocFileRespVO doc, List<String> warnings) {
    }

    /** 规则筛选结果：实际用于落盘的规则 + 已命中的原文（用于禁检关键词兜底） */
    private record RuleSelection(List<ReplacementRule> rules, List<String> replacedOlds) {
    }

    /**
     * 生成单份文档：
     * LLM 规则（带 scope）→ docx_replace(dryRun) 预演 → 仅保留命中规则（未带 scope 的多命中规则按位置展开）
     * → docx_replace 落盘 → docx_fill_fields 填主题封面字段 → docx_validate 自检（缺口则补规则重落盘）
     * → MinIO 上传 + 记录落库。
     */
    private OneResult generateOne(Long generationId, int index, DocPlanResult.DocItem item,
                                  String textDigest, String tablesDigest, String templateUrl,
                                  Path workDir, String ragContext,
                                  DocxValidateResult templateBaseline, Long userId) throws IOException {
        // 1. LLM 产出该份的 old → new 改写规则（重复文本须带 scope，且各位置 new 可不同）
        List<ReplacementRule> rules = aiContentService.buildReplacements(textDigest, tablesDigest, item, ragContext);

        // 2. dryRun 预演：先看每条规则的命中位置与命中数，避免「改错了才发现」
        DocxReplaceReport dry = rules.isEmpty()
                ? null
                : docxMcpGateway.replace(templateUrl, toRuleMaps(rules), null, true);

        // 3. 仅保留命中规则；未带 scope 且多处命中的规则按位置展开，杜绝全局替换
        RuleSelection selection = selectRules(rules, dry);

        // 4. 落盘：docx_replace 只改 w:t 文本，字体字号缩进编号与页面设置与模板完全一致
        String outputPath = workDir.resolve("doc-" + index + DOCX_EXT).toString();
        DocxReplaceReport applied = docxMcpGateway.replace(templateUrl, toRuleMaps(selection.rules()),
                outputPath, false);
        String docxPath = applied.getOutputPath();

        // 5. 封面主题字段（仅填用户明确给出的课程名称/章节名称/面向专业）
        docxPath = fillCoverFields(docxPath, item);

        // 6. 自检 + 缺口补规则重落盘（有限轮次）
        List<String> warnings = new ArrayList<>();
        selfCheckWithFixup(docxPath, item, textDigest, tablesDigest, selection, templateBaseline, warnings);

        // 7. 上传生成产物到 MinIO + infra_file 落库
        byte[] content = Files.readAllBytes(Path.of(docxPath));
        String fileName = buildFileName(item, index);
        FileUploadRespVO upload = fileService.uploadBytes(content, fileName, DOCX_CONTENT_TYPE);

        // 8. 保存结果文件记录
        DocTemplateFileDO fileDO = new DocTemplateFileDO();
        fileDO.setGenerationId(generationId);
        fileDO.setFileId(upload.getId());
        fileDO.setName(upload.getName());
        fileDO.setType("WORD");
        fileDO.setSize(upload.getSize());
        fileDO.setTag(truncate(item.getTag(), 50));
        fileDO.setSummary(truncate(item.getSummary(), 500));
        fileDO.setSort(index - 1);
        fileDO.setCreator(userId);
        fileDO.setUpdater(userId);
        fileMapper.insert(fileDO);

        DocFileRespVO vo = new DocFileRespVO();
        vo.setId(fileDO.getId());
        vo.setFileId(upload.getId());
        vo.setName(upload.getName());
        vo.setType("WORD");
        vo.setSize(upload.getSize());
        vo.setTag(fileDO.getTag());
        vo.setSummary(fileDO.getSummary());
        vo.setSrc(upload.getUrl());
        vo.setCreateTime(LocalDateTime.now());
        return new OneResult(vo, warnings);
    }

    /**
     * 依据 dryRun 报告筛选规则：
     * <ul>
     *   <li>未命中（matched=0）的规则丢弃；</li>
     *   <li>已带 scope 或仅一处命中的规则原样保留；</li>
     *   <li>未带 scope 且多处命中的规则，按 occurrences[].location 逐处展开为带 scope 的规则（禁止全局替换）。</li>
     * </ul>
     */
    private RuleSelection selectRules(List<ReplacementRule> rules, DocxReplaceReport dry) {
        if (rules.isEmpty()) {
            return new RuleSelection(List.of(), List.of());
        }
        // 对齐键：old + 规范化 scope。LLM 主路径会产生「同 old、不同 scope」的多条规则，
        // 仅以 old 归并会互相覆盖，导致误判命中/误留无 scope 规则。
        Map<String, DocxReplaceReport.RuleResult> resultByKey = new HashMap<>();
        Map<String, List<DocxReplaceReport.RuleResult>> resultsByOld = new HashMap<>();
        if (dry != null && dry.getResults() != null) {
            for (DocxReplaceReport.RuleResult result : dry.getResults()) {
                if (result == null || result.getOld() == null) {
                    continue;
                }
                resultByKey.put(ruleKey(result.getOld(), result.getScope()), result);
                resultsByOld.computeIfAbsent(result.getOld(), key -> new ArrayList<>()).add(result);
            }
        }

        List<ReplacementRule> kept = new ArrayList<>();
        List<String> replacedOlds = new ArrayList<>();
        List<String> missedOlds = new ArrayList<>();
        int expanded = 0;
        for (ReplacementRule rule : rules) {
            DocxReplaceReport.RuleResult result = resultByKey.get(ruleKey(rule.getOld(), rule.getScope()));
            if (result == null) {
                // scope 规范化差异时兜底：仅当该 old 只对应一条 dryRun 结果才采用，避免张冠李戴
                List<DocxReplaceReport.RuleResult> candidates = resultsByOld.get(rule.getOld());
                if (candidates != null && candidates.size() == 1) {
                    result = candidates.get(0);
                }
            }
            int matched = result == null ? 0 : result.matchedCount();
            if (matched == 0) {
                missedOlds.add(rule.getOld());
                continue;
            }
            replacedOlds.add(rule.getOld());
            if (rule.getScope() != null || matched == 1) {
                kept.add(rule);
                continue;
            }
            // 未带 scope 且多处命中：按位置逐处展开，避免「同一文字多处出现被全局替换」
            int expandedHere = 0;
            Map<String, Integer> seenPerLocation = new HashMap<>();
            if (result.getOccurrences() != null) {
                for (DocxReplaceReport.Occurrence occurrence : result.getOccurrences()) {
                    if (occurrence == null || occurrence.getLocation() == null
                            || occurrence.getLocation().isBlank()) {
                        continue;
                    }
                    String location = occurrence.getLocation();
                    int nth = seenPerLocation.merge(location, 1, Integer::sum);
                    Map<String, Object> scope = new LinkedHashMap<>();
                    scope.put("location", location);
                    if (nth > 1) {
                        // 同一段落内多处命中：scope 只能限定到段落，需用 occurrence 指明第几次
                        scope.put("occurrence", nth);
                    }
                    ReplacementRule scoped = new ReplacementRule();
                    scoped.setOld(rule.getOld());
                    scoped.setNewText(rule.getNewText());
                    scoped.setScope(scope);
                    kept.add(scoped);
                    expandedHere++;
                }
            }
            if (expandedHere == 0) {
                // 极少见（报告缺少位置信息）：退回原规则避免漏改，但显式告警，便于发现退化为全局替换
                log.warn("[selectRules] 规则 old={} 命中 {} 处但报告未给出位置信息，已退化为全局替换",
                        abbreviate(rule.getOld()), matched);
                kept.add(rule);
            } else {
                expanded += expandedHere;
            }
        }
        log.info("[selectRules] dryRun 结果: 规则={}, 命中={}, 未命中={}, 按位置展开={}, 未命中 old={}",
                rules.size(), replacedOlds.size(), missedOlds.size(), expanded, abbreviateList(missedOlds));
        return new RuleSelection(kept, replacedOlds);
    }

    /** 规则对齐键：old + 规范化 scope（scope 为空时退化为 old） */
    private String ruleKey(String old, Map<String, Object> scope) {
        return old + "|" + scopeKey(scope);
    }

    private String scopeKey(Map<String, Object> scope) {
        if (scope == null || scope.isEmpty()) {
            return "";
        }
        return scope.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    // ========== 私有方法：封面字段与自检 ==========

    /** 填充主题封面字段（仅用户明确给出时；人事与日期类字段一律不动，避免编造） */
    private String fillCoverFields(String docxPath, DocPlanResult.DocItem item) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIfNotBlank(fields, COVER_COURSE_NAME, item.getCourseName());
        putIfNotBlank(fields, COVER_CHAPTER_NAME, item.getChapterName());
        putIfNotBlank(fields, COVER_MAJOR, item.getMajor());
        if (fields.isEmpty()) {
            return docxPath;
        }
        try {
            Path source = Path.of(docxPath);
            Path temp = source.resolveSibling("cover-" + source.getFileName());
            String out = docxMcpGateway.fillFields(source.toString(), fields, temp.toString(), true);
            if (out == null) {
                return docxPath;
            }
            Files.move(Path.of(out), source, StandardCopyOption.REPLACE_EXISTING);
            log.info("[fillCoverFields] 封面字段已填充: {}", fields);
            return docxPath;
        } catch (Exception e) {
            log.warn("[fillCoverFields] 封面字段填充失败（已忽略）: {}", e.getMessage());
            return docxPath;
        }
    }

    /**
     * 自检 + 缺口修复闭环：体检 → 不干净则把「缺失的新关键词 / 残留的旧关键词」回喂模型补规则
     * → 增量重落盘 → 再体检，最多 {@code tb.doctemplate.max-fix-rounds} 轮。
     */
    private void selfCheckWithFixup(String docxPath, DocPlanResult.DocItem item,
                                    String textDigest, String tablesDigest, RuleSelection selection,
                                    DocxValidateResult baseline, List<String> warnings) {
        List<String> required = resolveRequiredKeywords(item, textDigest);
        List<String> forbidden = resolveForbiddenKeywords(item, selection.replacedOlds());
        int maxRounds = properties.getMaxFixRounds() == null ? 0 : Math.max(0, properties.getMaxFixRounds());
        DocxValidateResult check = null;

        for (int round = 0; round <= maxRounds; round++) {
            check = validateQuietly(docxPath, required, forbidden);
            if (check == null) {
                log.warn("[selfCheck] 文档体检未执行（docx_validate 不可用），跳过自检与修复");
                return;
            }
            logReport(round, check, baseline);
            if (isClean(check, baseline)) {
                break;
            }
            if (round == maxRounds) {
                log.warn("[selfCheck] 已达最大修复轮次 {}，仍有缺口: 缺新词={}, 残留旧词={}",
                        maxRounds, check.getMissingKeywords(), check.getForbiddenKeywordCounts());
                break;
            }
            List<ReplacementRule> fixups = aiContentService.buildFixups(textDigest, tablesDigest, item,
                    check.getMissingKeywords(), check.getForbiddenKeywordCounts());
            if (fixups.isEmpty()) {
                break;
            }
            RuleSelection fixSelection = selectRules(fixups,
                    docxMcpGateway.replace(docxPath, toRuleMaps(fixups), null, true));
            if (fixSelection.rules().isEmpty()) {
                log.info("[selfCheck] 第 {} 轮补规则未命中任何位置，停止修复", round + 1);
                break;
            }
            if (!applyIncrementalReplace(docxPath, workDirOf(docxPath), round + 1, fixSelection)) {
                break;
            }
        }

        // 将最终结论转化为任务告警（strict 模式下旧词残留/新词缺失视为「没改完」）
        if (check != null) {
            if (!structureSame(check, baseline)) {
                warnings.add("第 " + item.getIndex() + " 份（" + item.getTag() + "）自检：结构统计与模板不一致");
            }
            if (!Boolean.TRUE.equals(check.getPageSetupOk())) {
                warnings.add("第 " + item.getIndex() + " 份（" + item.getTag() + "）自检：页面设置不完整");
            }
            if (Boolean.TRUE.equals(properties.getStrictSelfCheck())) {
                if (check.getForbiddenKeywordCounts() != null && !check.getForbiddenKeywordCounts().isEmpty()) {
                    warnings.add("第 " + item.getIndex() + " 份（" + item.getTag() + "）自检：旧主题关键词仍残留 "
                            + check.getForbiddenKeywordCounts());
                }
                if (check.getMissingKeywords() != null && !check.getMissingKeywords().isEmpty()) {
                    warnings.add("第 " + item.getIndex() + " 份（" + item.getTag() + "）自检：新主题关键词缺失 "
                            + check.getMissingKeywords());
                }
            }
        }
    }

    /** 对已落盘产物做增量替换（写临时文件后原子覆盖，避免同一路径读写冲突） */
    private boolean applyIncrementalReplace(String docxPath, Path workDir, int round, RuleSelection selection) {
        try {
            Path source = Path.of(docxPath);
            Path temp = workDir.resolve("fix-" + round + "-" + source.getFileName());
            DocxReplaceReport report = docxMcpGateway.replace(source.toString(),
                    toRuleMaps(selection.rules()), temp.toString(), false);
            Files.move(Path.of(report.getOutputPath()), source, StandardCopyOption.REPLACE_EXISTING);
            log.info("[selfCheck] 第 {} 轮补规则已应用: 规则={}, applied={}",
                    round, selection.rules().size(), report.getAppliedRules());
            return true;
        } catch (Exception e) {
            log.warn("[selfCheck] 第 {} 轮补规则应用失败（停止修复）: {}", round, e.getMessage());
            return false;
        }
    }

    private Path workDirOf(String docxPath) {
        Path parent = Path.of(docxPath).getParent();
        return parent == null ? Path.of(".") : parent;
    }

    private void logReport(int round, DocxValidateResult check, DocxValidateResult baseline) {
        log.info("[selfCheck] 第 {} 轮报告: 结构一致={} 生成结构={} 模板结构={} | 空单元格={}(基线{}) "
                        + "| 页面设置完整={} | 旧词残留={} | 新词缺失={} | ok={}",
                round, structureSame(check, baseline), check.getStructure(),
                baseline == null ? null : baseline.getStructure(),
                check.getEmptyCellCount(), baseline == null ? null : baseline.getEmptyCellCount(),
                check.getPageSetupOk(), check.getForbiddenKeywordCounts(),
                check.getMissingKeywords(), check.getOk());
    }

    /** 自检是否「干净」：结构一致 + 页面设置完整 + 空单元格不超基线 + 无旧词残留 + 无新词缺失 */
    private boolean isClean(DocxValidateResult check, DocxValidateResult baseline) {
        return structureSame(check, baseline)
                && Boolean.TRUE.equals(check.getPageSetupOk())
                && emptyCellsOk(check, baseline)
                && (check.getForbiddenKeywordCounts() == null || check.getForbiddenKeywordCounts().isEmpty())
                && (check.getMissingKeywords() == null || check.getMissingKeywords().isEmpty());
    }

    private boolean structureSame(DocxValidateResult check, DocxValidateResult baseline) {
        return baseline == null || baseline.getStructure() == null
                || Objects.equals(baseline.getStructure(), check.getStructure());
    }

    private boolean emptyCellsOk(DocxValidateResult check, DocxValidateResult baseline) {
        return baseline == null || baseline.getEmptyCellCount() == null || check.getEmptyCellCount() == null
                || check.getEmptyCellCount() <= baseline.getEmptyCellCount();
    }

    /** 必备关键词：优先用规划给出的新主题词，缺失时回退为「模板中存在的结构锚点」 */
    private List<String> resolveRequiredKeywords(DocPlanResult.DocItem item, String textDigest) {
        List<String> fromPlan = cleanKeywords(item.getRequiredKeywords());
        if (!fromPlan.isEmpty()) {
            return fromPlan;
        }
        List<String> anchors = new ArrayList<>();
        for (String anchor : STRUCTURE_ANCHORS) {
            if (textDigest != null && textDigest.contains(anchor)) {
                anchors.add(anchor);
            }
            if (anchors.size() >= MAX_CHECK_KEYWORDS) {
                break;
            }
        }
        return anchors;
    }

    /** 禁用关键词：优先用规划给出的旧主题词，缺失时回退为「本次成功替换掉的原文」 */
    private List<String> resolveForbiddenKeywords(DocPlanResult.DocItem item, List<String> replacedOlds) {
        List<String> fromPlan = cleanKeywords(item.getForbiddenKeywords());
        if (!fromPlan.isEmpty()) {
            return fromPlan;
        }
        List<String> result = new ArrayList<>();
        for (String old : replacedOlds) {
            if (old == null || old.isBlank() || old.length() > MAX_KEYWORD_LENGTH || result.contains(old)) {
                continue;
            }
            result.add(old);
            if (result.size() >= MAX_CHECK_KEYWORDS) {
                break;
            }
        }
        return result;
    }

    /** 关键词清洗：去空白、限长、去重、限量 */
    private List<String> cleanKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_KEYWORD_LENGTH || cleaned.contains(trimmed)) {
                continue;
            }
            cleaned.add(trimmed);
            if (cleaned.size() >= MAX_CHECK_KEYWORDS) {
                break;
            }
        }
        return cleaned;
    }

    /** 自检调用：失败不影响主流程（基线或体检不可用时跳过） */
    private DocxValidateResult validateQuietly(String source, List<String> requiredKeywords,
                                               List<String> forbiddenKeywords) {
        try {
            return docxMcpGateway.validate(source, requiredKeywords, forbiddenKeywords);
        } catch (Exception e) {
            log.warn("[validateQuietly] 文档体检失败（已忽略）: source={}, error={}", source, e.getMessage());
            return null;
        }
    }

    /** 规则对象 → docx_replace 参数结构（键为 old / new，可选 scope） */
    private List<Map<String, Object>> toRuleMaps(List<ReplacementRule> rules) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (ReplacementRule rule : rules) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("old", rule.getOld());
            map.put("new", rule.getNewText());
            if (rule.getScope() != null && !rule.getScope().isEmpty()) {
                map.put("scope", rule.getScope());
            }
            maps.add(map);
        }
        return maps;
    }

    // ========== 私有方法：校验与组装 ==========

    private void validateRequest(GenerateReqVO reqVO) {
        if (reqVO == null || reqVO.getTemplateFileId() == null) {
            throw exception(TEMPLATE_FILE_ID_REQUIRED);
        }
        String description = reqVO.getDescription();
        if (description == null || description.isBlank()) {
            throw exception(DESCRIPTION_REQUIRED);
        }
        if (description.length() > DESCRIPTION_MAX) {
            throw exception(DESCRIPTION_TOO_LONG);
        }
    }

    /** 解析知识库空间 id：空 / "default" 表示不启用检索 */
    private Long parseSpaceId(String spaceId) {
        if (spaceId == null || spaceId.isBlank() || DEFAULT_SPACE_ID.equalsIgnoreCase(spaceId.trim())) {
            return null;
        }
        try {
            return Long.valueOf(spaceId.trim());
        } catch (NumberFormatException e) {
            log.warn("[parseSpaceId] 非法空间 id，已忽略检索增强: {}", spaceId);
            return null;
        }
    }

    /** 生成结果文件名：<标题>.docx（去除非法字符，超长截断） */
    private String buildFileName(DocPlanResult.DocItem item, int index) {
        String title = item.getTitle() == null ? "" : item.getTitle();
        String safe = title.replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s+", " ").trim();
        if (safe.isBlank()) {
            safe = "文档" + index;
        }
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return safe + DOCX_EXT;
    }

    private void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private GenerationHistoryRespVO buildHistoryVO(DocTemplateGenerationDO generation,
                                                   List<DocTemplateFileDO> files) {
        GenerationHistoryRespVO vo = new GenerationHistoryRespVO();
        vo.setId(generation.getId());
        vo.setTitle(truncate(collapse(generation.getDescription()), 20));
        vo.setKb(spaceName(generation.getSpaceId()));
        vo.setType("模板生成");
        vo.setTime(generation.getCreateTime() == null ? "" : generation.getCreateTime().format(TIME_FORMATTER));
        vo.setStatus(statusText(generation.getStatus()));
        vo.setDocCount(generation.getDocCount());
        vo.setDocs(buildDocSummaryList(files));
        return vo;
    }

    /** 历史记录内的文件摘要：只保留卡片字段，不签发预签名 URL */
    private List<DocFileRespVO> buildDocSummaryList(List<DocTemplateFileDO> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<DocFileRespVO> docs = new ArrayList<>(files.size());
        for (DocTemplateFileDO file : files) {
            docs.add(buildDocSummaryVO(file));
        }
        return docs;
    }

    private String spaceName(Long spaceId) {
        if (spaceId == null) {
            return "默认知识库";
        }
        String name = kbSpaceService.getSpaceName(spaceId);
        return name == null ? "默认知识库" : name;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "失败";
        }
        return switch (status) {
            case 1 -> "成功";
            case 2 -> "部分成功";
            default -> status == 0 ? "生成中" : "失败";
        };
    }

    /** 文件摘要字段（详情与历史共用；详情额外签发 src） */
    private DocFileRespVO buildDocSummaryVO(DocTemplateFileDO file) {
        DocFileRespVO vo = new DocFileRespVO();
        vo.setId(file.getId());
        vo.setFileId(file.getFileId());
        vo.setName(file.getName());
        vo.setType(file.getType());
        vo.setSize(file.getSize());
        vo.setTag(file.getTag());
        vo.setSummary(file.getSummary());
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }

    private DocFileRespVO buildDocVO(DocTemplateFileDO file) {
        DocFileRespVO vo = buildDocSummaryVO(file);
        // 根文件即 v1；current_version_id 非空时，卡片打开的是当前版本
        Long currentFileId = file.getFileId();
        if (file.getCurrentVersionId() != null) {
            DocTemplateVersionDO version = versionMapper.selectById(file.getCurrentVersionId());
            if (version != null && !Boolean.TRUE.equals(version.getSuperseded())) {
                currentFileId = version.getFileId();
            }
        }
        InfraFileDO infraFile = currentFileId == null ? null : infraFileMapper.selectById(currentFileId);
        if (infraFile != null) {
            vo.setFileId(currentFileId);
            vo.setSrc(freshUrl(infraFile));
            if (infraFile.getSize() != null) {
                vo.setSize(infraFile.getSize());
            }
        }
        return vo;
    }

    /** 重新签发预签名地址（入库时固化的 URL 存在有效期，读取时应重新签发） */
    private String freshUrl(InfraFileDO infraFile) {
        try {
            if (infraFile.getPath() != null && !infraFile.getPath().isBlank()) {
                return minioFileStorageService.getFileUrl(infraFile.getPath());
            }
        } catch (Exception e) {
            log.warn("[freshUrl] 重新签发文件地址失败，回退入库地址: {}", e.getMessage());
        }
        return infraFile.getUrl();
    }

    // ========== 私有方法：状态与清理 ==========

    private void markFailed(Long generationId, long startedAt, String errorMsg) {
        DocTemplateGenerationDO update = new DocTemplateGenerationDO();
        update.setId(generationId);
        update.setStatus(3);
        update.setDocCount(0);
        update.setErrorMsg(truncate(errorMsg, 500));
        update.setDurationMs(System.currentTimeMillis() - startedAt);
        generationMapper.updateResult(update);
    }

    /** 生成异常但非业务异常时的降级返回（保留 200 + 任务状态，避免前端只看到 500） */
    private GenerationRespVO failedResult(Long generationId, long startedAt, String errorMsg) {
        GenerationRespVO respVO = new GenerationRespVO();
        respVO.setGenerationId(generationId);
        respVO.setStatus(3);
        respVO.setDurationMs(System.currentTimeMillis() - startedAt);
        respVO.setErrorMsg(truncate(errorMsg, 500));
        respVO.setDocs(List.of());
        return respVO;
    }

    private String joinErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return truncate(String.join("；", errors), 500);
    }

    /** 递归清理本次任务的临时目录（含中间产物） */
    private void cleanUp(Path workDir) {
        if (workDir == null || !Files.exists(workDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 清理失败不影响主流程
                }
            });
        } catch (IOException e) {
            log.warn("[cleanUp] 清理临时目录失败: {}", e.getMessage());
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    /** 折叠空白字符（用于历史标题） */
    private String collapse(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    /** 截断列表用于日志 */
    private String abbreviateList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> trimmed = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            trimmed.add(abbreviate(value));
        }
        return trimmed.toString();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_KEYWORD_LENGTH ? text : text.substring(0, MAX_KEYWORD_LENGTH) + "…";
    }

}
