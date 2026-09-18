package org.example.backend.module.infra.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.infra.model.vo.FileUploadRespVO;
import org.example.backend.module.infra.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 通用文件接口（系统基础能力）
 */
@RestController
@RequestMapping("/infra/file")
public class FileController {

    @Resource
    private FileService fileService;

    /** 上传文件：存储到 MinIO 并返回文件元数据 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<FileUploadRespVO> upload(@RequestParam("file") MultipartFile file) {
        return success(fileService.uploadFile(file));
    }

}
