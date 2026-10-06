package org.example.backend.module.homework.service;

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
     * <p>当前仅接收并记录请求内容，真实批改能力待后续接入。
     *
     * @param reqVO 批改请求参数
     */
    void receiveGradeRequest(HomeworkGradeReqVO reqVO);

}
