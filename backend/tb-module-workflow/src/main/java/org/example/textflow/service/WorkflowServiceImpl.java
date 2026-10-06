package org.example.textflow.service;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.textflow.entity.WorkflowProcessLoader;
import org.example.textflow.entity.WorkflowProcessRespVO;
import org.example.textflow.model.WorkflowConstants;
import org.example.textflow.model.WorkflowDO;
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

    @Override
    public WorkflowProcessRespVO getProcess(String workflowId) {
        return loadProcess();
    }

    @Override
    public String saveWorkflow(WorkflowSaveReqVO reqVO) {
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
            workflowRepository.insert(workflow);
            log.info("[saveWorkflow] 新建工作流成功，id={}", workflow.getId());
            return workflow.getId();
        }

        if (!isNumeric(id)) {
            throw badRequest("工作流 id 非法: " + id);
        }
        WorkflowDO existing = workflowRepository.selectById(id)
                .orElseThrow(() -> badRequest("工作流不存在: " + id));
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
        WorkflowDetailRespVO respVO = new WorkflowDetailRespVO();
        respVO.setId(workflow.getId());
        respVO.setName(workflow.getName());
        respVO.setDescription(workflow.getDescription());
        respVO.setStatus(workflow.getStatus());
        respVO.setBuiltin(isBuiltin(workflow.getId()));
        respVO.setStepCount(countTopLevelNodes(workflow.getSchemaJson()));
        respVO.setSchemaJson(workflow.getSchemaJson());
        respVO.setUpdateTime(formatTime(workflow.getUpdateTime()));
        return respVO;
    }

    @Override
    public List<WorkflowVO> listWorkflows() {
        return workflowRepository.selectListOrderByUpdateTime().stream()
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
        try {
            WorkflowProcessLoader.parse(schemaJson);
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
