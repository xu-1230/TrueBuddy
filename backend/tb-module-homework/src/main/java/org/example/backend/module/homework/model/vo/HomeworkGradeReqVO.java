package org.example.backend.module.homework.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 批改请求 VO（第二步）
 *
 * <p>第一步上传完成后由前端串行提交，携带本次批改所需的全部配置。
 * 当前后端仅接收并记录，真实批改能力待后续接入。
 */
@Data
public class HomeworkGradeReqVO {

    /** 第一步上传返回的批改任务 id（用于关联本次批改与已上传的文件） */
    private Long taskId;
    /** 选中的知识库 id 列表（未选择时为空数组） */
    private List<String> spaceIds;
    /** 作业文件明细（顺序与第一步返回的 files 一致），含 fileId 与 MinIO 预签名 URL。
     *
     * <p>注意：预签名 URL 7 天过期，只作辅助信息（打开原文件 / 需要地址时现取）；
     * 后端解析与批改取文件务必以 fileId 为准：fileId → infra_file(bucket + path) → MinIO 字节流。
     * 若要把 URL 交给多模态模型，需确认该地址对模型侧可达（localhost 不可达），
     * 或改为后端读字节后以 base64 内联提交。
     */
    private List<HomeworkFileItemVO> files;
    /** AI 描述说明（批改要求） */
    private String description;
    /** 导出结果格式：xlsx / csv */
    private String exportFormat;
    /** 选择的工作流 id */
    private String workflowId;

}
