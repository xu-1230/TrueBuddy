package org.example.backend.module.homework.service;

import org.example.backend.module.homework.model.HomeworkTaskDO;
import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
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
     * <p>把批改要求与知识库写入 {@code homework_task}，工作流的开始节点随后按 {@code taskId}
     * 回查本次批改的真实输入（文件清单取自 {@code homework_file}）。
     *
     * @param reqVO 批改请求参数
     */
    void receiveGradeRequest(HomeworkGradeReqVO reqVO);

    /**
     * 按主键取批改任务（含批改要求与知识库），供工作流开始节点使用。
     *
     * @param taskId 批改任务 id
     * @return 任务；不存在时为 {@code null}
     */
    HomeworkTaskDO getTask(Long taskId);

    /**
     * 按任务回查作业文件明细，供工作流开始节点产出 {@code file_tree}。
     *
     * <p>以 {@code homework_file} 为准（{@code fileId} 是模型读原文的唯一凭据），
     * {@code type} / {@code url} 取自 {@code infra_file}。
     *
     * @param taskId 批改任务 id
     * @return 按相对路径升序的文件明细
     */
    List<HomeworkFileItemVO> listFileItems(Long taskId);

}
