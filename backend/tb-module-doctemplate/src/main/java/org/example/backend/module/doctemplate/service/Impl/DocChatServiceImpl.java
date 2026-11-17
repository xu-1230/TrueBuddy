package org.example.backend.module.doctemplate.service.Impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.doctemplate.config.DocTemplateProperties;
import org.example.backend.module.doctemplate.mapper.DocTemplateChatMessageMapper;
import org.example.backend.module.doctemplate.mapper.DocTemplateFileMapper;
import org.example.backend.module.doctemplate.mapper.DocTemplateGenerationMapper;
import org.example.backend.module.doctemplate.mapper.DocTemplateVersionMapper;
import org.example.backend.module.doctemplate.model.DocTemplateChatMessageDO;
import org.example.backend.module.doctemplate.model.DocTemplateFileDO;
import org.example.backend.module.doctemplate.model.DocTemplateGenerationDO;
import org.example.backend.module.doctemplate.model.DocTemplateVersionDO;
import org.example.backend.module.doctemplate.model.vo.ChatEditReqVO;
import org.example.backend.module.doctemplate.model.vo.ChatHistoryMessage;
import org.example.backend.module.doctemplate.model.vo.ChatMessageRespVO;
import org.example.backend.module.doctemplate.model.vo.ChatSelection;
import org.example.backend.module.doctemplate.model.vo.ChatUndoReqVO;
import org.example.backend.module.doctemplate.model.vo.ChatUndoRespVO;
import org.example.backend.module.doctemplate.model.vo.DocEditBlock;
import org.example.backend.module.doctemplate.model.vo.DocEditOperation;
import org.example.backend.module.doctemplate.model.vo.DocEditPlan;
import org.example.backend.module.doctemplate.model.vo.DocxReplaceReport;
import org.example.backend.module.doctemplate.service.AiContentService;
import org.example.backend.module.doctemplate.service.DocChatService;
import org.example.backend.module.doctemplate.service.DocxMcpGateway;
import org.example.backend.module.doctemplate.service.TemplateDigestBuilder;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.model.InfraFileDO;
import org.example.backend.module.infra.model.vo.FileUploadRespVO;
import org.example.backend.module.infra.service.FileService;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_EDIT_INVALID;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_INSTRUCTION_REQUIRED;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_INSTRUCTION_TOO_LONG;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_ROOT_FILE_REQUIRED;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_UNDO_INVALID;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_VERSION_CONFLICT;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_VERSION_NOT_EXISTS;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.CHAT_WORKSPACE_NOT_EXISTS;

/**
 * 文档 AI 编辑实现：SSE 阶段事件 + LLM 结构化指令 + docx-mcp 落盘 + 版本表。
 *
 * <p>聊天记录按根文件落库（doc_template_chat_message）：用户指令、助手回复、
 * 系统撤回提示与失效状态都在一轮对话中写入，打开文档时由 history 接口回放；
 * 请求里的 history 仍作为本轮 LLM 上下文。生成结果列表继续只展示根文件（不展示版本）。
 */
@Slf4j
@Service
public class DocChatServiceImpl implements DocChatService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int TEXT_PAGE_SIZE = 200;
    private static final int TEXT_MAX_PAGES = 30;
    private static final int MAX_INSTRUCTION = 2000;
    private static final int MAX_SELECTIONS = 5;
    private static final int MAX_SUMMARY = 500;
    private static final int CHAT_HISTORY_DEFAULT_LIMIT = 200;
    private static final int CHAT_HISTORY_MAX_LIMIT = 500;
    private static final int MAX_LLM_HISTORY = 12;

    private static final Pattern BODY_PARAGRAPH = Pattern.compile("^p:\\d+$");
    private static final Pattern TABLE = Pattern.compile("^t:\\d+$");
    private static final Pattern CELL = Pattern.compile("^t:\\d+/r:\\d+/c:\\d+$");
    private static final Pattern CELL_PARAGRAPH = Pattern.compile("^t:\\d+/r:\\d+/c:\\d+/p:\\d+$");
    private static final Pattern PARAGRAPH = Pattern.compile("^(p:\\d+|t:\\d+/r:\\d+/c:\\d+/p:\\d+)$");
    private static final Pattern PARAGRAPH_INDEX = Pattern.compile("p:(\\d+)$");

    @Resource
    private DocTemplateFileMapper fileMapper;
    @Resource
    private DocTemplateGenerationMapper generationMapper;
    @Resource
    private DocTemplateVersionMapper versionMapper;
    @Resource
    private DocTemplateChatMessageMapper chatMessageMapper;
    @Resource
    private InfraFileMapper infraFileMapper;
    @Resource
    private MinioFileStorageService minioFileStorageService;
    @Resource
    private FileService fileService;
    @Resource
    private DocxMcpGateway docxMcpGateway;
    @Resource
    private TemplateDigestBuilder digestBuilder;
    @Resource
    private AiContentService aiContentService;
    @Resource
    private DocTemplateProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 编辑请求占用较长（LLM + docx）；请求线程只负责返回 SseEmitter */
    private final ExecutorService chatExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "doc-chat");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void shutdown() {
        chatExecutor.shutdownNow();
    }

    @Override
    public SseEmitter chat(ChatEditReqVO reqVO) {
        if (reqVO == null || reqVO.getRootFileId() == null) {
            throw exception(CHAT_ROOT_FILE_REQUIRED);
        }
        String instruction = reqVO.getInstruction() == null ? "" : reqVO.getInstruction().trim();
        if (instruction.isEmpty()) {
            throw exception(CHAT_INSTRUCTION_REQUIRED);
        }
        if (instruction.length() > MAX_INSTRUCTION) {
            throw exception(CHAT_INSTRUCTION_TOO_LONG);
        }
        Long userId = currentUserId();
        DocTemplateFileDO root = requireOwnedRoot(reqVO.getRootFileId(), userId);
        SseEmitter emitter = new SseEmitter(0L);
        chatExecutor.execute(() -> runChat(emitter, reqVO, instruction, root, userId));
        return emitter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatUndoRespVO undo(ChatUndoReqVO reqVO) {
        if (reqVO == null || reqVO.getRootFileId() == null || reqVO.getTargetVersionId() == null) {
            throw exception(CHAT_UNDO_INVALID, "缺少 rootFileId 或 targetVersionId");
        }
        Long userId = currentUserId();
        DocTemplateFileDO root = requireOwnedRoot(reqVO.getRootFileId(), userId);
        DocTemplateVersionDO target = versionMapper.selectById(reqVO.getTargetVersionId());
        if (target == null || !Objects.equals(target.getRootFileId(), root.getId())
                || Boolean.TRUE.equals(target.getSuperseded())) {
            throw exception(CHAT_UNDO_INVALID, "版本不存在、不属于该文档或已失效");
        }
        if (!isOnCurrentChain(root, target.getId())) {
            throw exception(CHAT_UNDO_INVALID, "目标版本不在当前版本链上");
        }
        List<Long> discarded = versionMapper.selectSubtreeIds(root.getId(), target.getId());
        versionMapper.supersedeSubtree(root.getId(), target.getId());
        Long baseVersionId = target.getParentVersionId();
        fileMapper.updateCurrentVersion(root.getId(), baseVersionId);

        // 聊天记录与版本同步：目标版本的助手消息及其后消息置失效，并补一条系统提示
        DocTemplateChatMessageDO targetMessage =
                chatMessageMapper.selectFirstByVersionId(root.getId(), target.getId());
        Long boundaryId = targetMessage == null ? null : targetMessage.getId();
        if (boundaryId == null && !discarded.isEmpty()) {
            // 旧版本或消息写入失败时，用被丢弃版本里最早的一条消息作为边界
            boundaryId = chatMessageMapper.selectMinIdByVersionIds(root.getId(), discarded);
        }
        int laterEdits = boundaryId == null ? 0
                : chatMessageMapper.countValidEditsAfter(root.getId(), boundaryId);
        if (boundaryId != null) {
            chatMessageMapper.supersedeFromId(root.getId(), boundaryId);
        }
        String noticeText = laterEdits > 0
                ? "已撤回到上一版，之后 " + laterEdits + " 条修改消息已失效。"
                : "已撤回到上一版。";
        DocTemplateChatMessageDO notice = persistMessage(root.getId(), "system", noticeText,
                null, null, null, false, userId);

        ChatUndoRespVO respVO = new ChatUndoRespVO();
        respVO.setRootFileId(root.getId());
        respVO.setDiscardedVersionIds(discarded);
        respVO.setSrc(srcForVersion(root, baseVersionId));
        respVO.setNotice(notice == null ? null : toRespVO(notice));
        log.info("[undo] 文档撤回: root={}, targetVersion={}, discarded={}, current={}, messageBoundary={}",
                root.getId(), target.getId(), discarded, baseVersionId, boundaryId);
        return respVO;
    }

    @Override
    public List<ChatMessageRespVO> getHistory(Long rootFileId, Integer limit) {
        if (rootFileId == null) {
            throw exception(CHAT_ROOT_FILE_REQUIRED);
        }
        Long userId = currentUserId();
        requireOwnedRoot(rootFileId, userId);
        int size = limit == null || limit <= 0
                ? CHAT_HISTORY_DEFAULT_LIMIT : Math.min(limit, CHAT_HISTORY_MAX_LIMIT);
        return chatMessageMapper.selectRecentByRootFileId(rootFileId, size).stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());
    }

    // ========== SSE 执行 ==========

    private void runChat(SseEmitter emitter, ChatEditReqVO reqVO, String instruction,
                         DocTemplateFileDO root, Long userId) {
        Path workDir = null;
        boolean assistantSaved = false;
        try {
            send(emitter, "status", status("analyzing", "正在读取当前版本与选中元素…"));
            DocTemplateFileDO currentRoot = fileMapper.selectById(root.getId());
            if (currentRoot == null) {
                throw exception(CHAT_WORKSPACE_NOT_EXISTS);
            }
            // 上下文先取持久化历史（排除失效/失败），再落本轮用户消息
            List<ChatSelection> selections = normalizeSelections(reqVO.getSelections());
            List<ChatHistoryMessage> history = loadLlmHistory(currentRoot.getId(), reqVO.getHistory());
            // 用户消息尽早落库：后续读取/模型/落盘失败时，回放仍能看到本轮请求与错误
            persistMessage(currentRoot.getId(), "user", instruction,
                    null, writeJson(selections), null, false, userId);

            DocTemplateVersionDO currentVersion = resolveCurrentVersion(currentRoot);
            Long baseVersionId = currentVersion == null ? null : currentVersion.getId();
            String currentUrl = currentFileUrl(currentRoot, currentVersion);
            workDir = Path.of(properties.getTempDir(), "chat",
                    currentRoot.getId() + "-" + System.currentTimeMillis());
            Files.createDirectories(workDir);

            String textDigest = fetchAllText(currentUrl);
            String tablesDigest = digestBuilder.digestTables(docxMcpGateway.getTables(currentUrl));

            send(emitter, "status", status("analyzing", "正在理解修改要求…"));
            DocEditPlan plan = aiContentService.edit(instruction, textDigest, tablesDigest,
                    selections, history, List.of());

            AppliedEdit applied = null;
            if (plan.getOperations() != null && !plan.getOperations().isEmpty()) {
                try {
                    applied = applyEditPlan(emitter, currentUrl, plan.getOperations(), workDir);
                } catch (EditValidationException first) {
                    send(emitter, "status", status("repairing", "校验未通过，正在让 AI 修正…"));
                    DocEditPlan repaired = aiContentService.edit(instruction, textDigest, tablesDigest,
                            selections, history, first.getErrors());
                    if (repaired.getOperations() == null || repaired.getOperations().isEmpty()) {
                        List<DocEditBlock> noopReply = completeReply(repaired.getReply(), "未修改文档。");
                        persistAssistant(currentRoot.getId(), noopReply, null, userId);
                        assistantSaved = true;
                        sendAssistant(emitter, noopReply, null, null);
                        sendDone(emitter);
                        return;
                    }
                    try {
                        applied = applyEditPlan(emitter, currentUrl, repaired.getOperations(), workDir);
                        plan = repaired;
                    } catch (EditValidationException second) {
                        throw exception(CHAT_EDIT_INVALID, String.join("；", second.getErrors()));
                    }
                }
            }

            if (applied == null) {
                List<DocEditBlock> noopReply = completeReply(plan.getReply(), "未修改文档。");
                persistAssistant(currentRoot.getId(), noopReply, null, userId);
                assistantSaved = true;
                sendAssistant(emitter, noopReply, null, null);
                sendDone(emitter);
                return;
            }

            // 版本冲突保护：LLM 执行期间当前版本被其它操作改掉，则放弃本次编辑
            DocTemplateFileDO latest = fileMapper.selectById(currentRoot.getId());
            if (latest == null || !Objects.equals(latest.getCurrentVersionId(), baseVersionId)) {
                throw exception(CHAT_VERSION_CONFLICT);
            }

            send(emitter, "status", status("uploading", "正在保存新版本…"));
            String summary = summaryOf(applied.changeCount(), plan.getReply());
            byte[] content = Files.readAllBytes(Path.of(applied.finalPath()));
            String fileName = currentRoot.getName() == null || currentRoot.getName().isBlank()
                    ? "document.docx" : currentRoot.getName();
            FileUploadRespVO upload = fileService.uploadBytes(content, fileName, DOCX_CONTENT_TYPE);

            int revision = versionMapper.selectMaxRevision(currentRoot.getId()) + 1;
            DocTemplateVersionDO version = new DocTemplateVersionDO();
            version.setRootFileId(currentRoot.getId());
            version.setFileId(upload.getId());
            version.setParentVersionId(baseVersionId);
            version.setRevision(revision);
            version.setEditSummary(truncate(summary, MAX_SUMMARY));
            version.setEditOps(writeJson(plan.getOperations()));
            version.setCreator(userId);
            versionMapper.insert(version);
            fileMapper.updateCurrentVersion(currentRoot.getId(), version.getId());

            List<DocEditBlock> reply = completeReply(plan.getReply(), "已完成本次修改。");
            appendSummary(reply, applied.changeCount());
            persistAssistant(currentRoot.getId(), reply, version.getId(), userId);
            assistantSaved = true;
            sendAssistant(emitter, reply, version.getId(), summary);
            send(emitter, "version", Map.of("src", upload.getUrl(), "rootFileId", currentRoot.getId()));
            sendDone(emitter);
            log.info("[chat] 编辑完成: root={}, version={}, revision={}, ops={}, src={}",
                    currentRoot.getId(), version.getId(), revision, applied.changeCount(), upload.getUrl());
        } catch (EditValidationException e) {
            String message = String.join("；", e.getErrors());
            persistError(root, userId, message, assistantSaved);
            sendError(emitter, message);
        } catch (ServiceException e) {
            persistError(root, userId, e.getMessage(), assistantSaved);
            sendError(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("[chat] 文档编辑失败: root={}", root == null ? null : root.getId(), e);
            String message = "修改失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage());
            persistError(root, userId, message, assistantSaved);
            sendError(emitter, message);
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    // ========== 编辑指令校验与落盘 ==========

    private AppliedEdit applyEditPlan(SseEmitter emitter, String currentUrl,
                                      List<DocEditOperation> operations, Path workDir) throws IOException {
        List<String> errors = new ArrayList<>();
        List<DocEditOperation> replaces = new ArrayList<>();
        List<DocEditOperation> setCells = new ArrayList<>();
        List<DocEditOperation> structural = new ArrayList<>();

        for (DocEditOperation op : operations) {
            if (op == null || op.getOp() == null) {
                continue;
            }
            String name = op.getOp().trim();
            op.setOp(name);
            switch (name) {
                case "replace" -> validateReplace(op, errors, replaces);
                case "setCell" -> validateSetCell(op, errors, setCells);
                case "insertParagraph", "deleteParagraph" -> validateStructural(op, errors, structural);
                default -> errors.add("不支持的操作：" + name);
            }
        }
        if (!errors.isEmpty()) {
            throw new EditValidationException(errors);
        }
        if (replaces.isEmpty() && setCells.isEmpty() && structural.isEmpty()) {
            return null;
        }

        String source = currentUrl;
        // 1. replace：先 dryRun 看命中，确认无歧义后批量落盘
        if (!replaces.isEmpty()) {
            List<Map<String, Object>> rules = replaces.stream().map(this::toReplaceRule).collect(Collectors.toList());
            DocxReplaceReport dry;
            try {
                dry = docxMcpGateway.replace(currentUrl, rules, null, true);
            } catch (ServiceException e) {
                throw new EditValidationException(List.of(e.getMessage()));
            }
            if (dry.getMissedRules() != null && !dry.getMissedRules().isEmpty()) {
                List<String> missed = dry.getMissedRules().stream()
                        .map(rule -> "replace 未命中：old=" + abbreviate(rule.getOld()))
                        .collect(Collectors.toList());
                throw new EditValidationException(missed);
            }
            try {
                DocxReplaceReport applied = docxMcpGateway.replace(currentUrl, rules,
                        workDir.resolve("step-replace.docx").toString(), false);
                source = applied.getOutputPath();
            } catch (ServiceException e) {
                throw new EditValidationException(List.of(e.getMessage()));
            }
        }

        // 2. setCell：单元格坐标不受正文段落增删影响，放在结构操作前
        int index = 0;
        for (DocEditOperation op : setCells) {
            try {
                Map<String, Object> result = docxMcpGateway.setCell(source, nullToEmpty(op.getText()),
                        op.getLocator(), workDir.resolve("step-cell-" + index++ + ".docx").toString());
                source = outputPath(result, source);
            } catch (ServiceException e) {
                throw new EditValidationException(List.of("setCell 失败：" + e.getMessage()));
            }
        }

        // 3. 结构操作按段落号倒序执行，避免前面的增删让后面的 p:N 漂移
        structural.sort(Comparator.comparingInt(this::structuralAnchorIndex).reversed());
        for (DocEditOperation op : structural) {
            try {
                if ("insertParagraph".equals(op.getOp())) {
                    Map<String, Object> result = docxMcpGateway.insertParagraph(source, nullToEmpty(op.getText()),
                            blankToNull(op.getAfter()), blankToNull(op.getBefore()), blankToNull(op.getStyleFrom()),
                            workDir.resolve("step-struct-" + index++ + ".docx").toString());
                    source = outputPath(result, source);
                } else {
                    Map<String, Object> result = docxMcpGateway.deleteParagraph(source, op.getLocator(),
                            workDir.resolve("step-struct-" + index++ + ".docx").toString());
                    source = outputPath(result, source);
                }
            } catch (ServiceException e) {
                throw new EditValidationException(List.of(op.getOp() + " 失败：" + e.getMessage()));
            }
        }

        send(emitter, "status", status("validating", "正在校验版式…"));
        Map<String, Object> diff;
        try {
            diff = docxMcpGateway.diff(currentUrl, source, "format");
        } catch (ServiceException e) {
            throw new EditValidationException(List.of("文档比对失败：" + e.getMessage()));
        }
        Object format = diff.get("format");
        if (format instanceof Map<?, ?> formatMap && Boolean.FALSE.equals(formatMap.get("pageSetupEqual"))) {
            throw new EditValidationException(List.of("修改改变了页面设置，已放弃本次编辑"));
        }
        int count = replaces.size() + setCells.size() + structural.size();
        return new AppliedEdit(source, count);
    }

    private void validateReplace(DocEditOperation op, List<String> errors, List<DocEditOperation> target) {
        String locator = normalizeLocator(op.getLocator());
        op.setLocator(locator);
        if (locator == null || locator.isBlank()
                || !(BODY_PARAGRAPH.matcher(locator).matches() || TABLE.matcher(locator).matches()
                || CELL.matcher(locator).matches() || CELL_PARAGRAPH.matcher(locator).matches())) {
            errors.add("replace 的 locator 无效：" + op.getLocator());
            return;
        }
        if (op.getOldText() == null || op.getOldText().isBlank()) {
            errors.add("replace 缺少 old");
            return;
        }
        if (op.getOldText().contains("\n") || op.getOldText().contains("\r")) {
            errors.add("replace 的 old 不能跨段落：" + abbreviate(op.getOldText()));
            return;
        }
        if (Objects.equals(op.getOldText(), op.getNewText())) {
            errors.add("replace 的 new 与 old 相同：" + abbreviate(op.getOldText()));
            return;
        }
        target.add(op);
    }

    private void validateSetCell(DocEditOperation op, List<String> errors, List<DocEditOperation> target) {
        String locator = normalizeLocator(op.getLocator());
        op.setLocator(locator);
        if (locator == null || !CELL.matcher(locator).matches()) {
            errors.add("setCell 的 locator 必须是单元格路径：" + op.getLocator());
            return;
        }
        target.add(op);
    }

    private void validateStructural(DocEditOperation op, List<String> errors, List<DocEditOperation> target) {
        if ("insertParagraph".equals(op.getOp())) {
            String after = normalizeLocator(op.getAfter());
            String before = normalizeLocator(op.getBefore());
            op.setAfter(after);
            op.setBefore(before);
            if (after != null && before != null) {
                errors.add("insertParagraph 只能指定 after 或 before");
                return;
            }
            String anchor = after != null ? after : before;
            if (anchor == null || !PARAGRAPH.matcher(anchor).matches()) {
                errors.add("insertParagraph 的锚点必须是段落：" + (anchor == null ? "（空）" : anchor));
                return;
            }
            if (op.getStyleFrom() != null && !op.getStyleFrom().isBlank()) {
                String styleFrom = normalizeLocator(op.getStyleFrom());
                op.setStyleFrom(styleFrom);
                if (!PARAGRAPH.matcher(styleFrom).matches()) {
                    errors.add("insertParagraph 的 styleFrom 必须是段落：" + styleFrom);
                    return;
                }
            }
            target.add(op);
            return;
        }
        String locator = normalizeLocator(op.getLocator());
        op.setLocator(locator);
        if (locator == null || !PARAGRAPH.matcher(locator).matches()) {
            errors.add("deleteParagraph 的 locator 必须是段落：" + op.getLocator());
            return;
        }
        target.add(op);
    }

    private Map<String, Object> toReplaceRule(DocEditOperation op) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("old", op.getOldText());
        rule.put("new", nullToEmpty(op.getNewText()));
        rule.put("scope", scopeOf(op.getLocator()));
        return rule;
    }

    /** locator → docx_replace 的 scope */
    private Map<String, Object> scopeOf(String locator) {
        if (BODY_PARAGRAPH.matcher(locator).matches() || CELL_PARAGRAPH.matcher(locator).matches()) {
            return Map.of("location", locator);
        }
        if (TABLE.matcher(locator).matches()) {
            return Map.of("table", Integer.parseInt(locator.substring(2)));
        }
        Matcher matcher = CELL.matcher(locator);
        if (matcher.matches()) {
            String[] parts = locator.substring(2).split("/");
            int table = Integer.parseInt(parts[0]);
            int row = Integer.parseInt(parts[1].substring(2));
            int col = Integer.parseInt(parts[2].substring(2));
            return Map.of("table", table, "row", row, "cell", col);
        }
        return Map.of();
    }

    private int structuralAnchorIndex(DocEditOperation op) {
        String locator = "insertParagraph".equals(op.getOp())
                ? (op.getAfter() != null ? op.getAfter() : op.getBefore())
                : op.getLocator();
        Matcher matcher = PARAGRAPH_INDEX.matcher(nullToEmpty(locator));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private String normalizeLocator(String locator) {
        if (locator == null) {
            return null;
        }
        String value = locator.trim();
        if (value.matches("\\d+")) {
            return "p:" + value;
        }
        return value.isEmpty() ? null : value;
    }

    // ========== 版本 / 文件辅助 ==========

    private Long currentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        }
        return loginUser.getId();
    }

    /** 根文件即工作区；只有根文件 creator（或生成记录 creator）可以访问 */
    private DocTemplateFileDO requireOwnedRoot(Long rootFileId, Long userId) {
        DocTemplateFileDO root = fileMapper.selectById(rootFileId);
        if (root == null) {
            throw exception(CHAT_WORKSPACE_NOT_EXISTS);
        }
        DocTemplateGenerationDO generation = root.getGenerationId() == null
                ? null : generationMapper.selectById(root.getGenerationId());
        Long owner = root.getCreator() != null
                ? root.getCreator()
                : (generation == null ? null : generation.getCreator());
        if (!Objects.equals(owner, userId)) {
            throw exception(CHAT_WORKSPACE_NOT_EXISTS);
        }
        return root;
    }

    private DocTemplateVersionDO resolveCurrentVersion(DocTemplateFileDO root) {
        if (root.getCurrentVersionId() == null) {
            return null;
        }
        DocTemplateVersionDO version = versionMapper.selectById(root.getCurrentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getSuperseded())
                || !Objects.equals(version.getRootFileId(), root.getId())) {
            throw exception(CHAT_VERSION_NOT_EXISTS);
        }
        return version;
    }

    private String currentFileUrl(DocTemplateFileDO root, DocTemplateVersionDO version) {
        Long fileId = version == null ? root.getFileId() : version.getFileId();
        String url = fileUrl(fileId);
        if (url == null) {
            throw exception(CHAT_WORKSPACE_NOT_EXISTS);
        }
        return url;
    }

    private boolean isOnCurrentChain(DocTemplateFileDO root, Long targetVersionId) {
        Long cursor = root.getCurrentVersionId();
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(targetVersionId)) {
                return true;
            }
            DocTemplateVersionDO version = versionMapper.selectById(cursor);
            if (version == null || Boolean.TRUE.equals(version.getSuperseded())) {
                return false;
            }
            cursor = version.getParentVersionId();
        }
        return false;
    }

    private String srcForVersion(DocTemplateFileDO root, Long versionId) {
        if (versionId == null) {
            return fileUrl(root.getFileId());
        }
        DocTemplateVersionDO version = versionMapper.selectById(versionId);
        return version == null ? fileUrl(root.getFileId()) : fileUrl(version.getFileId());
    }

    private String fileUrl(Long fileId) {
        if (fileId == null) {
            return null;
        }
        InfraFileDO file = infraFileMapper.selectById(fileId);
        if (file == null) {
            return null;
        }
        try {
            if (file.getPath() != null && !file.getPath().isBlank()) {
                return minioFileStorageService.getFileUrl(file.getPath());
            }
        } catch (Exception e) {
            log.warn("[fileUrl] 重新签发文件地址失败，回退入库地址: {}", e.getMessage());
        }
        return file.getUrl();
    }

    // ========== 上下文 / 回复辅助 ==========

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
            log.warn("[fetchAllText] 文档共 {} 页，仅取前 {} 页", pages, TEXT_MAX_PAGES);
        }
        return builder.toString();
    }

    private List<ChatSelection> normalizeSelections(List<ChatSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        return selections.stream()
                .filter(Objects::nonNull)
                .filter(selection -> selection.getLocator() != null && !selection.getLocator().isBlank())
                .limit(MAX_SELECTIONS)
                .collect(Collectors.toList());
    }

    private List<DocEditBlock> completeReply(List<DocEditBlock> reply, String fallback) {
        if (reply != null && !reply.isEmpty()) {
            return new ArrayList<>(reply);
        }
        DocEditBlock block = new DocEditBlock();
        block.setType("p");
        block.setText(fallback);
        return new ArrayList<>(List.of(block));
    }

    private void appendSummary(List<DocEditBlock> reply, int count) {
        DocEditBlock block = new DocEditBlock();
        block.setType("p");
        block.setText("已修改 " + count + " 处，文档已更新。");
        reply.add(block);
    }

    private String summaryOf(int count, List<DocEditBlock> reply) {
        if (reply != null) {
            for (DocEditBlock block : reply) {
                if (block != null && "p".equals(block.getType())
                        && block.getText() != null && !block.getText().isBlank()) {
                    return truncate(block.getText().trim(), MAX_SUMMARY);
                }
            }
        }
        return "已修改 " + count + " 处";
    }

    private Map<String, Object> status(String stage, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", stage);
        payload.put("message", message);
        return payload;
    }

    private void sendAssistant(SseEmitter emitter, List<DocEditBlock> reply, Long versionId, String summary)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reply", reply);
        if (versionId != null) {
            payload.put("versionId", versionId);
        }
        if (summary != null && !summary.isBlank()) {
            payload.put("summary", summary);
        }
        send(emitter, "assistant", payload);
    }

    private void sendDone(SseEmitter emitter) {
        try {
            send(emitter, "done", Map.of());
        } catch (Exception e) {
            log.debug("[chat] SSE done 发送失败（客户端可能已断开）: {}", e.getMessage());
        } finally {
            emitter.complete();
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        log.warn("[chat] 编辑失败: {}", message);
        try {
            send(emitter, "error", Map.of("message", message == null ? "修改失败" : message));
        } catch (Exception e) {
            log.debug("[chat] SSE error 发送失败: {}", e.getMessage());
        } finally {
            emitter.complete();
        }
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private String outputPath(Map<String, Object> result, String fallback) {
        Object path = result == null ? null : result.get("outputPath");
        return path == null ? fallback : path.toString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[writeJson] 编辑指令序列化失败: {}", e.getMessage());
            return null;
        }
    }

    // ========== 聊天记录辅助 ==========

    /** 聊天记录最佳努力落库：失败只记日志，不阻断 SSE */
    private DocTemplateChatMessageDO persistMessage(Long rootFileId, String role, String content,
                                                    String blocks, String selections, Long versionId,
                                                    boolean error, Long creator) {
        try {
            DocTemplateChatMessageDO message = new DocTemplateChatMessageDO();
            message.setRootFileId(rootFileId);
            message.setRole(role);
            message.setContent(content == null ? "" : content);
            message.setBlocks(blocks);
            message.setSelections(selections);
            message.setVersionId(versionId);
            message.setSuperseded(false);
            message.setError(error);
            message.setCreator(creator);
            message.setCreateTime(LocalDateTime.now());
            chatMessageMapper.insert(message);
            return message;
        } catch (Exception e) {
            log.warn("[chat] 聊天记录写入失败: root={}, role={}, error={}",
                    rootFileId, role, e.getMessage());
            return null;
        }
    }

    private void persistAssistant(Long rootFileId, List<DocEditBlock> reply, Long versionId, Long userId) {
        persistMessage(rootFileId, "assistant", flattenBlocks(reply), writeJson(reply),
                null, versionId, false, userId);
    }

    private void persistError(DocTemplateFileDO root, Long userId, String message, boolean assistantSaved) {
        if (root == null || assistantSaved) {
            return;
        }
        String content = message == null || message.isBlank() ? "修改失败" : message;
        persistMessage(root.getId(), "assistant", content, null, null, null, true, userId);
    }

    /** LLM 上下文：优先持久化历史（最近 12 条有效消息）；没有记录时回退前端 history */
    private List<ChatHistoryMessage> loadLlmHistory(Long rootFileId, List<ChatHistoryMessage> clientFallback) {
        try {
            List<DocTemplateChatMessageDO> rows =
                    chatMessageMapper.selectRecentValidByRootFileId(rootFileId, MAX_LLM_HISTORY);
            if (!rows.isEmpty()) {
                return rows.stream()
                        .filter(row -> row.getContent() != null && !row.getContent().isBlank())
                        .map(row -> {
                            ChatHistoryMessage message = new ChatHistoryMessage();
                            message.setRole(row.getRole());
                            message.setContent(row.getContent());
                            return message;
                        })
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("[chat] 持久化历史读取失败，回退前端 history: root={}, err={}",
                    rootFileId, e.getMessage());
        }
        return sanitizeClientHistory(clientFallback);
    }

    private List<ChatHistoryMessage> sanitizeClientHistory(List<ChatHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatHistoryMessage> valid = history.stream()
                .filter(Objects::nonNull)
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                .collect(Collectors.toList());
        if (valid.size() <= MAX_LLM_HISTORY) {
            return valid;
        }
        return new ArrayList<>(valid.subList(valid.size() - MAX_LLM_HISTORY, valid.size()));
    }

    private String flattenBlocks(List<DocEditBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (DocEditBlock block : blocks) {
            if (block == null) {
                continue;
            }
            if ("ul".equals(block.getType()) && block.getItems() != null) {
                for (String item : block.getItems()) {
                    appendLine(builder, "- " + (item == null ? "" : item));
                }
            } else {
                appendLine(builder, block.getText());
            }
        }
        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private ChatMessageRespVO toRespVO(DocTemplateChatMessageDO message) {
        ChatMessageRespVO vo = new ChatMessageRespVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setBlocks(readJsonList(message.getBlocks(), new TypeReference<List<DocEditBlock>>() {
        }));
        vo.setSelections(readJsonList(message.getSelections(), new TypeReference<List<ChatSelection>>() {
        }));
        vo.setVersionId(message.getVersionId());
        vo.setSuperseded(Boolean.TRUE.equals(message.getSuperseded()));
        vo.setError(Boolean.TRUE.equals(message.getError()));
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("[chat] 聊天记录 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void deleteQuietly(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return;
            }
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                        // cleanup best effort
                    }
                });
            }
        } catch (IOException e) {
            log.debug("[chat] 临时目录清理失败: {}", e.getMessage());
        }
    }

    /** 本轮应用结果：最终 docx 路径 + 修改条数 */
    private record AppliedEdit(String finalPath, int changeCount) {
    }

    /** 编辑指令校验失败：错误清单回喂 LLM 修正，或最终报给前端 */
    private static class EditValidationException extends RuntimeException {

        private final List<String> errors;

        EditValidationException(List<String> errors) {
            super(String.join("；", errors));
            this.errors = errors;
        }

        List<String> getErrors() {
            return errors;
        }
    }
}
