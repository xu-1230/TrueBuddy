package org.example.backend.module.workflow.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.workflow.model.vo.WorkflowDetailRespVO;
import org.example.backend.module.workflow.model.vo.WorkflowProcessRespVO;
import org.example.backend.module.workflow.model.vo.WorkflowRunReqVO;
import org.example.backend.module.workflow.model.vo.WorkflowSaveReqVO;
import org.example.backend.module.workflow.model.vo.WorkflowVO;
import org.example.backend.module.workflow.service.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 自定义工作流接口。
 *
 * <p>执行模型：{@code POST /workflow/run} 提交任务并以 **SSE** 实时推送进度事件
 * （{@code created} / {@code message} / {@code completed} / {@code error} / {@code done}）；
 * 执行到人工确认节点时暂停，调用方再次调用同一接口（带 {@code executeId} + {@code confirmedPlan}）
 * 提交清单，事件继续推回原连接。
 *
 * <p>{@code GET /workflow/get_process?workflow_id=} 为**轮询兜底**：断线重连 / 刷新页面后仍能取回
 * 进度与终态；{@code exeHistoryStatus} 为 1=执行中 / 2=成功 / 3=失败 / 4=等待用户输入（非终态）。
 *
 * <p>免登录放行说明：安全组件的 {@code @PermitAll} 仅支持方法级注解扫描（当前实现未采集注解），
 * 因此本模块统一通过 {@code tb.security.permit-all-urls} 放行 {@code /workflow/**}。
 */
@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    @Resource
    private WorkflowService workflowService;

    /**
     * 提交执行 / 恢复人工确认（**单接口双语义**，两种分支均返回 SSE）。
     *
     * <ul>
     *   <li>不带 {@code executeId} → 新建执行：事件流为
     *       {@code created → message* → completed → done}；若停在人工确认节点，则推
     *       {@code message}(requires_action) 后**保持连接打开**；</li>
     *   <li>带 {@code executeId}（等待态会话）+ {@code confirmedPlan} → 恢复执行：事件继续推回
     *       **原连接**，本响应只推一条 {@code done}（负载 {@code {"accepted":true}}）作确认应答。</li>
     * </ul>
     *
     * <p>两种分支的响应类型统一为 {@code text/event-stream}，因此返回类型声明为 {@link SseEmitter}。
     */
    @PostMapping("/run")
    public SseEmitter run(@RequestBody(required = false) WorkflowRunReqVO reqVO) {
        // 带执行会话 id 即视为「恢复等待态会话」，否则为新建执行
        if (reqVO != null && reqVO.getExecuteId() != null && !reqVO.getExecuteId().isBlank()) {
            return workflowService.resume(reqVO);
        }
        return workflowService.runStream(reqVO);
    }

    /**
     * 查询执行状态：SSE 的轮询兜底，建议每 2 秒一次；终态为 2（成功）/ 3（失败），
     * 4（等待用户输入）时继续轮询并据 {@code pendingConfirm} 渲染人工确认卡片。
     */
    @GetMapping("/get_process")
    public CommonResult<WorkflowProcessRespVO> getProcess(@RequestParam("workflow_id") String workflowId) {
        return success(workflowService.getProcess(workflowId));
    }

    /**
     * 保存工作流定义：入参 {@code id} 为空时新建（返回新 id），非空时更新。
     *
     * <p>保存即覆盖当前记录，不做版本管理。
     *
     * <p>返回类型为 String：雪花 ID 是 19 位长整型，超出 JS {@code Number} 的安全整数范围
     * （2^53），以数字返回会被前端四舍五入而失真，故与列表 / 详情的 {@code workflow_id} 统一为字符串。
     */
    @PostMapping("/save")
    public CommonResult<String> save(@RequestBody WorkflowSaveReqVO reqVO) {
        return success(String.valueOf(workflowService.saveWorkflow(reqVO)));
    }

    /** 查询工作流详情（含画布结构 schema_json） */
    @GetMapping("/detail")
    public CommonResult<WorkflowDetailRespVO> detail(@RequestParam("id") Long id) {
        return success(workflowService.getWorkflowDetail(id));
    }

    /** 工作流列表（不含画布结构，按更新时间倒序） */
    @GetMapping("/list")
    public CommonResult<List<WorkflowVO>> list() {
        return success(workflowService.listWorkflows());
    }

}
