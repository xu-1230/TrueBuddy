package org.example.backend.module.workflow.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 发起工作流执行入参（**单接口双语义**：新建执行 / 恢复人工确认）。
 *
 * <p>新建执行：不传 {@code executeId} 与 {@code confirmedPlan}（{@code schemaJson} 必填）；
 * 服务端建立会话并以 SSE 推送 {@code created → message* → completed → done}。
 *
 * <p>恢复执行：传 {@code executeId}（等待态会话）+ {@code confirmedPlan}；服务端从人工确认断点续跑，
 * 后续事件继续推回**原来那条 SSE 连接**，本次请求本身只返回「已受理」。
 */
@Data
public class WorkflowRunReqVO {

    /** 画布 schema（JSON 字符串）；新建执行时必填，为空时提交报「工作流定义为空，无法执行」 */
    private String schemaJson;

    /** 执行会话 id：新建执行时不传（服务端生成）；恢复执行时必传（等待态会话 id） */
    private String executeId;

    /** 恢复执行时的已确认清单（作业文件路径列表）；非空且 {@code executeId} 有值即视为恢复执行 */
    private List<String> confirmedPlan;

    /** 可选：本次执行所用画布对应的工作流定义 id（用于响应中的 workFlowId；画布直接传入时可不传） */
    private String workflowId;

    /** 可选：开始节点输入（按变量名覆盖，如 fileTree / knowledgeList / aiDescription / fullScore） */
    private Map<String, Object> inputs;

    /** 文件夹文件树（相对路径列表，目录以 / 结尾） */
    private List<String> fileTree;

    /** 知识库列表（知识库 id / 名称） */
    private List<String> knowledgeList;

    /** AI 批改说明 */
    private String aiDescription;

    /** 本次批改满分 */
    private Integer fullScore;

}
