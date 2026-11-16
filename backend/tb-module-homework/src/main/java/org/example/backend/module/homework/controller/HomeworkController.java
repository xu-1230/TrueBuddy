package org.example.backend.module.homework.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteReqVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteRespVO;
import org.example.backend.module.homework.model.vo.HomeworkTaskDetailRespVO;
import org.example.backend.module.homework.model.vo.HomeworkTaskItemVO;
import org.example.backend.module.homework.model.vo.HomeworkUploadRespVO;
import org.example.backend.module.homework.service.HomeworkService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 作业批改接口。
 *
 * <p>批改为两步式：
 * <ol>
 *     <li>POST /upload —— 批量上传作业文件（multipart：files + paths + folderName），
 *     逐份存储并落库相对路径，返回以相对路径表示的文件清单</li>
 *     <li>POST /grade —— 提交批改请求（JSON：taskId + spaceIds + files + description
 *     + exportFormat + workflowId）；仅落库存档，批改**执行**所需的数据由前端随
 *     {@code POST /chat} 的 inputs 直传，执行以 inputs 为准</li>
 * </ol>
 *
 * <p>任务回看与执行：GET /task/list（最近任务列表）、GET /task/detail（配置 + 文件树 + 评分只读回看）、
 * POST /task/start（状态 0→1，前端在 /grade 之后、跑工作流之前调用）、
 * POST /{taskId}/results（终态回写：评分记录覆盖式落库，状态收敛 2/3/4）。
 */
@RestController
@RequestMapping("/homework")
public class HomeworkController {

    @Resource
    private HomeworkService homeworkService;

    /** 作业文件夹上传：files 与 paths 按顺序一一对应 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<HomeworkUploadRespVO> upload(@RequestParam("files") List<MultipartFile> files,
                                                     @RequestParam(value = "paths", required = false) List<String> paths,
                                                     @RequestParam(value = "folderName", required = false) String folderName) {
        return success(homeworkService.uploadAndBuildTree(files, paths, folderName));
    }

    /** 提交批改请求：前端在上传成功后串行调用，携带本次批改的全部配置 */
    @PostMapping("/grade")
    public CommonResult<Boolean> grade(@RequestBody HomeworkGradeReqVO reqVO) {
        homeworkService.receiveGradeRequest(reqVO);
        return success(true);
    }

    /** 批改任务列表（历史抽屉）：最近 N 条，limit 非正取默认值 */
    @GetMapping("/task/list")
    public CommonResult<List<HomeworkTaskItemVO>> taskList(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return success(homeworkService.getTaskList(limit));
    }

    /** 批改任务详情（只读回看）：任务信息 + 批改配置 + 文件树 */
    @GetMapping("/task/detail")
    public CommonResult<HomeworkTaskDetailRespVO> taskDetail(@RequestParam("taskId") Long taskId) {
        return success(homeworkService.getTaskDetail(taskId));
    }

    /** 开始执行批改：任务状态 0（待批改）→ 1（批改中），前端在 /grade 之后、跑工作流之前调用 */
    @PostMapping("/task/start")
    public CommonResult<Boolean> startTask(@RequestParam("taskId") Long taskId) {
        homeworkService.startGrading(taskId);
        return success(true);
    }

    /** 回写批改结果（执行终态后由前端调用）：逐学生评分覆盖式落库，任务状态收敛到终态 2/3/4 */
    @PostMapping("/{taskId}/results")
    public CommonResult<HomeworkResultWriteRespVO> writeResults(@PathVariable("taskId") Long taskId,
                                                                @RequestBody HomeworkResultWriteReqVO reqVO) {
        return success(homeworkService.writeResults(taskId, reqVO));
    }

}
