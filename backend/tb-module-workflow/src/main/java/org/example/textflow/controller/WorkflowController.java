package org.example.textflow.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.textflow.model.vo.WorkflowDetailRespVO;
import org.example.textflow.model.vo.WorkflowSaveReqVO;
import org.example.textflow.model.vo.WorkflowVO;
import org.example.textflow.node.NodeEventListener;
import org.example.textflow.node.WorkflowGraphFactory;
import org.example.textflow.service.WorkflowService;
import org.example.textflow.tool.WorkflowToolCallbacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 工作流定义接口（保存 / 详情 / 列表 + 联调）。
 *
 * <p><b>执行入口已合并到 {@code POST /chat}</b>：原先的 {@code /workflow/run} 与
 * {@code /workflow/resume} 已废弃——执行语义（会话、执行实例、中断续跑）统一由
 * {@code ChatController} 承载，本控制器只保留定义相关的读写，避免两套执行口径并存。
 *
 * <p>定义来源：{@code /chat} 只接受已落库的 {@code workflow_id}，故执行侧按
 * {@code workflow} 表读取；本控制器只负责定义的保存与查询。
 */
@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    /** 联调接口使用的定义标识（非数字 id → 回落 classpath text.json） */
    private static final String DEFAULT_WORKFLOW_ID = "text";

    @Resource
    private WorkflowService workflowService;

    /** 大模型节点使用的对话模型客户端（bean 定义见 {@code WorkflowChatClientConfiguration}） */
    @Resource(name = "nodeChatClient")
    private ChatClient nodeChatClient;

    /** 大模型节点可自主调用的工具（当前为作业文件读取），装配图时传给节点 */
    @Resource
    private WorkflowToolCallbacks workflowToolCallbacks;

    /** 联调执行同样需要检查点存储（编译期要求），与 /chat 的执行各自独立 */
    private final MemorySaver checkpointSaver = new MemorySaver();

    /**
     * 联调接口：同步执行 classpath 定义一次并把终态状态键打到控制台。
     * <p>不是业务接口，仅供本地验证画布定义是否能跑通。
     */
    @GetMapping("/text")
    public void text() throws GraphStateException {
        CompiledGraph compiledGraph = WorkflowGraphFactory.createGraph(
                workflowService.getProcess(DEFAULT_WORKFLOW_ID), NodeEventListener.NO_OP, nodeChatClient, checkpointSaver,
                workflowToolCallbacks.callbacks());
        compiledGraph.invoke(Map.of())
                // 只记状态键：终态里是各节点的产出（用户数据），不整体打进日志
                .ifPresent(state -> log.info("/workflow/text 执行结束，终态状态键: {}", state.data().keySet()));
    }

    /**
     * 保存工作流定义：{@code id} 为空表示新建（生成雪花 ID），非空表示更新该记录。
     * <p>画布定义整体存入 {@code schema_json}，不做版本管理，保存即覆盖；
     * 校验（名称必填、画布 JSON 结构与连线 id）在 Service 内完成，失败返回 {@code code = 400}。
     *
     * @param reqVO 保存入参（名称、说明、画布定义 JSON）
     * @return 工作流 id（雪花 ID 字符串，避免前端 JS 丢精度）
     */
    @PostMapping("/save")
    public CommonResult<String> save(@RequestBody WorkflowSaveReqVO reqVO) {
        return success(workflowService.saveWorkflow(reqVO));
    }

    /**
     * 工作流详情（含画布定义 JSON）。
     *
     * @param workflowId 工作流 id（雪花 ID 字符串）
     */
    @GetMapping("/detail")
    public CommonResult<WorkflowDetailRespVO> detail(@RequestParam("workflow_id") String workflowId) {
        return success(workflowService.getWorkflowDetail(workflowId));
    }

    /** 工作流列表（不含画布定义，按更新时间倒序） */
    @GetMapping("/list")
    public CommonResult<List<WorkflowVO>> list() {
        return success(workflowService.listWorkflows());
    }

}
