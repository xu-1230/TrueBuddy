package org.example.textflow.service;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.textflow.entity.WorkflowProcessLoader;
import org.example.textflow.entity.WorkflowProcessRespVO;
import org.example.textflow.model.WorkflowConstants;
import org.example.textflow.model.WorkflowDO;
import org.example.textflow.model.vo.PublicWorkflowVO;
import org.example.textflow.model.vo.WorkflowDetailRespVO;
import org.example.textflow.model.vo.WorkflowSaveReqVO;
import org.example.textflow.model.vo.WorkflowVO;
import org.example.textflow.repository.WorkflowDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceImpl.class);

    /** 名称长度上限（与 workflow 表的 varchar(100) 对齐） */
    private static final int NAME_MAX_LENGTH = 100;

    /** 说明长度上限（与 workflow 表的 varchar(500) 对齐） */
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    /** 可见性：私有（ADR 0006：新建一律私有，公有能力在共享链路打开） */
    private static final int VISIBILITY_PRIVATE = 0;

    /** 可见性：公有 */
    private static final int VISIBILITY_PUBLIC = 1;

    /** fork 命名判重的最大尝试次数（防极端重名兜底，正常几轮内必然命中） */
    private static final int FORK_NAME_MAX_RETRY = 99;

    /** 详情 / 列表里的时间展示格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 只用于读取画布定义的顶层 nodes 数组长度（算 stepCount），不做 POJO 反序列化 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 工作流定义加载后缓存，避免每次请求都读文件 */
    private volatile WorkflowProcessRespVO processCache;

    private final WorkflowDefinitionRepository workflowRepository;

    public WorkflowServiceImpl(WorkflowDefinitionRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    /**
     * 取可执行的画布定义。
     *
     * <p>{@code /chat} 只接受已落库的 {@code workflow_id}，故数字 id 一律按 {@code workflow} 表读取
     * （{@code schema_json} 整体反序列化）；非数字 id（如联调接口 {@code /workflow/text} 传的
     * {@code "text"}）回落 classpath {@code text.json}，保持联调链路不受影响。
     */
    @Override
    public WorkflowProcessRespVO getProcess(String workflowId) {
        String id = trimToNull(workflowId);
        if (id == null || !isNumeric(id)) {
            return loadProcess();
        }
        WorkflowDO workflow = workflowRepository.selectById(id)
                .orElseThrow(() -> badRequest("工作流不存在: " + id));
        return parseSchema(workflow.getSchemaJson());
    }

    @Override
    public String saveWorkflow(WorkflowSaveReqVO reqVO) {
        // /workflow/** 在免登录白名单内（URL 模式无法按方法拆分），写操作在服务层强制登录
        Long userId = currentUserId();
        if (userId == null) {
            throw unauthorized();
        }
        String name = trimToNull(reqVO.getName());
        if (name == null) {
            throw badRequest("工作流名称不能为空");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw badRequest("工作流名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        String description = trimToNull(reqVO.getDescription());
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw badRequest("工作流说明不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        String schemaJson = trimToNull(reqVO.getSchemaJson());
        if (schemaJson == null) {
            throw badRequest("画布定义不能为空");
        }
        validateSchema(schemaJson);

        String id = trimToNull(reqVO.getId());
        if (id == null) {
            WorkflowDO workflow = new WorkflowDO();
            workflow.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
            workflow.setName(name);
            workflow.setDescription(description);
            workflow.setSchemaJson(schemaJson);
            workflow.setStatus(WorkflowConstants.STATUS_DRAFT);
            workflow.setVisibility(VISIBILITY_PRIVATE);
            workflow.setCreator(userId);
            workflowRepository.insert(workflow);
            log.info("[saveWorkflow] 新建工作流成功，id={}", workflow.getId());
            return workflow.getId();
        }

        if (isBuiltin(id)) {
            // 内置模板是种子数据，任何账号（含其归属者）都不可通过画布保存改写
            throw forbidden("内置模板不可修改");
        }
        if (!isNumeric(id)) {
            throw badRequest("工作流 id 非法: " + id);
        }
        WorkflowDO existing = workflowRepository.selectById(id)
                .orElseThrow(() -> badRequest("工作流不存在: " + id));
        assertWorkflowOwner(existing, userId, "无权修改该工作流");
        existing.setName(name);
        existing.setDescription(description);
        existing.setSchemaJson(schemaJson);
        if (existing.getStatus() == null) {
            existing.setStatus(WorkflowConstants.STATUS_DRAFT);
        }
        workflowRepository.update(existing);
        log.info("[saveWorkflow] 更新工作流成功，id={}", id);
        return id;
    }

    @Override
    public WorkflowDetailRespVO getWorkflowDetail(String workflowId) {
        String id = trimToNull(workflowId);
        if (id == null) {
            throw badRequest("工作流 id 不能为空");
        }
        WorkflowDO workflow = workflowRepository.selectById(id)
                .orElseThrow(() -> badRequest("工作流不存在: " + id));
        // 可读口径：builtin / 本人 / 他人公有（US19，只读形态由前端呈现，写操作仍有归属校验）
        Long userId = currentUserId();
        boolean owner = userId != null && userId.equals(workflow.getCreator());
        boolean publicVisible = workflow.getVisibility() != null && workflow.getVisibility() == VISIBILITY_PUBLIC;
        if (!owner && !isBuiltin(workflow.getId()) && !publicVisible) {
            throw forbidden("无权查看该工作流");
        }
        WorkflowDetailRespVO respVO = new WorkflowDetailRespVO();
        respVO.setId(workflow.getId());
        respVO.setName(workflow.getName());
        respVO.setDescription(workflow.getDescription());
        respVO.setStatus(workflow.getStatus());
        respVO.setVisibility(workflow.getVisibility());
        respVO.setMine(owner);
        respVO.setBuiltin(isBuiltin(workflow.getId()));
        respVO.setStepCount(countTopLevelNodes(workflow.getSchemaJson()));
        respVO.setSchemaJson(workflow.getSchemaJson());
        respVO.setUpdateTime(formatTime(workflow.getUpdateTime()));
        return respVO;
    }

    @Override
    public void updateVisibility(String workflowId, Integer visibility) {
        // /workflow/** 在免登录白名单内，写操作在服务层强制登录
        Long userId = currentUserId();
        if (userId == null) {
            throw unauthorized();
        }
        if (visibility == null || (visibility != VISIBILITY_PRIVATE && visibility != VISIBILITY_PUBLIC)) {
            throw badRequest("可见性取值无效，只能是 0（私有）或 1（公有）");
        }
        String id = trimToNull(workflowId);
        if (id == null) {
            throw badRequest("工作流 id 不能为空");
        }
        if (isBuiltin(id)) {
            throw forbidden("内置模板不可修改");
        }
        WorkflowDO workflow = workflowRepository.selectById(id)
                .orElseThrow(() -> badRequest("工作流不存在: " + id));
        assertWorkflowOwner(workflow, userId, "无权修改该工作流");
        workflowRepository.updateVisibility(id, visibility);
        log.info("[updateVisibility] 工作流可见性已切换，id={}，visibility={}", id, visibility);
    }

    @Override
    public List<PublicWorkflowVO> listPublicWorkflows() {
        return workflowRepository.selectListPublic(currentUserId()).stream()
                .map(workflow -> {
                    PublicWorkflowVO vo = new PublicWorkflowVO();
                    vo.setId(workflow.getId());
                    vo.setName(workflow.getName());
                    vo.setDescription(workflow.getDescription());
                    vo.setStatus(workflow.getStatus());
                    vo.setBuiltin(false);
                    vo.setStepCount(countTopLevelNodes(workflow.getSchemaJson()));
                    vo.setUpdateTime(formatTime(workflow.getUpdateTime()));
                    vo.setCreatorName(workflow.getCreatorName());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public String forkWorkflow(String workflowId) {
        Long userId = currentUserId();
        if (userId == null) {
            throw unauthorized();
        }
        String id = trimToNull(workflowId);
        if (id == null) {
            throw badRequest("工作流 id 不能为空");
        }
        WorkflowDO workflow = workflowRepository.selectById(id)
                .orElseThrow(() -> badRequest("工作流不存在: " + id));
        if (isBuiltin(id)) {
            throw forbidden("内置模板不可 fork");
        }
        // fork 口径与详情可读一致：本人或他人公有
        boolean owner = userId.equals(workflow.getCreator());
        boolean publicVisible = workflow.getVisibility() != null && workflow.getVisibility() == VISIBILITY_PUBLIC;
        if (!owner && !publicVisible) {
            throw forbidden("无权 fork 该工作流");
        }

        WorkflowDO copy = new WorkflowDO();
        copy.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
        copy.setName(uniqueForkNameOf(workflow.getName()));
        copy.setDescription(workflow.getDescription());
        copy.setSchemaJson(workflow.getSchemaJson());
        copy.setStatus(WorkflowConstants.STATUS_DRAFT);
        copy.setVisibility(VISIBILITY_PRIVATE);
        copy.setCreator(userId);
        workflowRepository.insert(copy);
        log.info("[forkWorkflow] 工作流已 fork，originId={}，copyId={}，creator={}", id, copy.getId(), userId);
        return copy.getId();
    }

    /** fork 副本命名：「原名（副本）」，重名时叠加序号（副本 2 / 副本 3 …），统一截到列宽内 */
    private String uniqueForkNameOf(String originName) {
        String base = originName == null || originName.isBlank() ? "未命名工作流" : originName.trim();
        for (int seq = 1; seq <= FORK_NAME_MAX_RETRY; seq++) {
            String suffix = seq == 1 ? "（副本）" : "（副本 " + seq + "）";
            int allowed = NAME_MAX_LENGTH - suffix.length();
            String candidate = (base.length() > allowed ? base.substring(0, allowed) : base) + suffix;
            if (workflowRepository.selectIdByName(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("fork 命名重试次数耗尽: " + base);
    }

    @Override
    public List<WorkflowVO> listWorkflows() {
        return workflowRepository.selectListVisible(currentUserId()).stream()
                .map(workflow -> {
                    WorkflowVO vo = new WorkflowVO();
                    vo.setId(workflow.getId());
                    vo.setName(workflow.getName());
                    vo.setDescription(workflow.getDescription());
                    vo.setStatus(workflow.getStatus());
                    vo.setBuiltin(isBuiltin(workflow.getId()));
                    vo.setStepCount(countTopLevelNodes(workflow.getSchemaJson()));
                    vo.setUpdateTime(formatTime(workflow.getUpdateTime()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 校验画布定义：JSON 结构可反序列化，且连线 id 非空唯一（复用定义加载器的既有口径） */
    private void validateSchema(String schemaJson) {
        parseSchema(schemaJson);
    }

    /** 反序列化画布定义，失败转成可直接展示的业务异常 */
    private WorkflowProcessRespVO parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw badRequest("画布定义不能为空");
        }
        try {
            return WorkflowProcessLoader.parse(schemaJson);
        } catch (IOException e) {
            throw badRequest("画布定义不是合法的 JSON: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
    }

    /** 顶层节点数：只数 nodes 数组长度，迭代体子块（内嵌 blocks）不计入 */
    private int countTopLevelNodes(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode nodes = MAPPER.readTree(schemaJson).path("nodes");
            return nodes.isArray() ? nodes.size() : 0;
        } catch (Exception e) {
            // 画布定义异常不影响列表 / 详情可用，按 0 个步骤展示
            log.warn("[countTopLevelNodes] 画布定义无法解析，按 0 个步骤计: {}", e.getMessage());
            return 0;
        }
    }

    /** 是否内置模板：按固定 id 判定，与启动初始化写入的种子记录同源 */
    private boolean isBuiltin(String id) {
        return WorkflowConstants.BUILTIN_WORKFLOW_ID.equals(id);
    }

    /** 当前登录用户 id；/workflow/** 是免登录白名单，匿名请求返回 null */
    private Long currentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getId();
    }

    /** 归属校验：非创建者按 403 拒绝（ADR 0006：creator 为归属唯一真相源） */
    private void assertWorkflowOwner(WorkflowDO workflow, Long userId, String message) {
        if (userId == null || !userId.equals(workflow.getCreator())) {
            throw forbidden(message);
        }
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static ServiceException badRequest(String message) {
        return new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

    private static ServiceException unauthorized() {
        return new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
    }

    private static ServiceException forbidden(String message) {
        return new ServiceException(GlobalErrorCodeConstants.FORBIDDEN.getCode(), message);
    }

    private WorkflowProcessRespVO loadProcess() {
        WorkflowProcessRespVO process = processCache;
        if (process == null) {
            synchronized (this) {
                process = processCache;
                if (process == null) {
                    process = readProcess();
                    processCache = process;
                }
            }
        }
        return process;
    }

    private WorkflowProcessRespVO readProcess() {
        try {
            return WorkflowProcessLoader.loadDefault();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "加载工作流定义失败: " + WorkflowProcessLoader.DEFAULT_LOCATION, e);
        }
    }

}
