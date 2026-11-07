package org.example.backend.module.homework.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
import org.example.backend.module.homework.model.vo.HomeworkUploadRespVO;
import org.example.backend.module.homework.service.HomeworkService;
import org.springframework.http.MediaType;
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
        System.out.println(reqVO);
        homeworkService.receiveGradeRequest(reqVO);
        return success(true);
    }

    

}
