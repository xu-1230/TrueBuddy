package org.example.backend.module.homework.service;

import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteReqVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteRespVO;
import org.example.backend.module.homework.model.vo.HomeworkTaskDetailRespVO;
import org.example.backend.module.homework.model.vo.HomeworkTaskItemVO;
import org.example.backend.module.homework.model.vo.HomeworkUploadRespVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 作业批改 Service
 */
public interface HomeworkService {

    /**
     * 上传一次作业文件夹内的全部文件，并返回以相对路径表示的文件清单。
     *
     * <p>逐份走统一文件存储（MinIO + infra_file），同时在 homework_task / homework_file
     * 落库任务与每份作业的相对路径。
     *
     * @param files      待上传作业，顺序与 paths 严格一致
     * @param paths      文件相对路径，如 电子技术基础-第3次作业/20230101-张三/第三章习题.docx
     * @param folderName 作业文件夹显示名，可为空（为空时由首个相对路径的根目录推断）
     * @return 任务 id、相对路径清单与每份文件明细
     */
    HomeworkUploadRespVO uploadAndBuildTree(List<MultipartFile> files, List<String> paths, String folderName);

    /**
     * 接收批改请求（第二步）：携带知识库、文件树、批改要求、导出格式与工作流。
     *
     * <p>把批改要求与知识库写入 {@code homework_task} 落库存档（历史 / 管理端可查）。
     * 注意：批改**执行**所需的数据不由后端按 taskId 回查，而是前端随 {@code POST /chat}
     * 的 {@code inputs} 直传（file_tree / description / know），执行以 inputs 为准。
     *
     * @param reqVO 批改请求参数
     */
    void receiveGradeRequest(HomeworkGradeReqVO reqVO);

    /**
     * 批改任务列表（历史抽屉）：最近 N 条，按创建时间倒序。
     *
     * <p>口径同文档生成历史（{@code DocTemplateService.getHistory}）：
     * limit 非正时取默认值（20），超过上限（100）时截断。
     *
     * @param limit 条数上限，可空
     * @return 任务列表（含状态与批改配置摘要）
     */
    List<HomeworkTaskItemVO> getTaskList(Integer limit);

    /**
     * 批改任务详情（历史抽屉点条目后的只读回看）：任务信息 + 批改配置 + 文件树。
     *
     * @param taskId 任务 id
     * @return 任务详情（files 按相对路径升序，即目录树阅读顺序）
     */
    HomeworkTaskDetailRespVO getTaskDetail(Long taskId);

    /**
     * 开始执行批改：任务状态从「已上传待批改（0）」推进到「批改中（1）」。
     *
     * <p>状态机只允许 0→1：任务不存在抛 NOT_EXISTS，已不在待批改状态抛 STATUS_INVALID。
     *
     * @param taskId 任务 id
     */
    void startGrading(Long taskId);

    /**
     * 覆盖式回写批改结果（执行终态后由前端调用）。
     *
     * <p>同一任务重复回写以最后一次为准（物理删除旧行后整批插入）。终态（2 成功 /
     * 3 部分成功 / 4 失败）与漏批份数由服务端从 records 数量对比任务 fileCount 推导，
     * 请求体不携带；状态机只允许从批改中（1）或既有终态推进，待批改（0）不可直接写终态。
     *
     * @param taskId 任务 id
     * @param reqVO  逐学生评分记录（records）+ 会话溯源（conversationId）+ 汇总产出（statistics，仅日志）
     * @return 回写后的终态、记录数与漏批份数
     */
    HomeworkResultWriteRespVO writeResults(Long taskId, HomeworkResultWriteReqVO reqVO);

}
