package org.example.backend.module.knowledgebase.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.knowledgebase.model.vo.DocumentRespVO;
import org.example.backend.module.knowledgebase.model.vo.DocumentSaveReqVO;
import org.example.backend.module.knowledgebase.service.KbDocumentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 知识库文档接口
 */
@RestController
@RequestMapping("/knowledge-base/document")
public class KbDocumentController {

    @Resource
    private KbDocumentService documentService;

    @GetMapping("/list")
    public CommonResult<List<DocumentRespVO>> list(@RequestParam("spaceId") Long spaceId) {
        return success(documentService.getDocumentList(spaceId));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody DocumentSaveReqVO reqVO) {
        return success(documentService.createDocument(reqVO));
    }

    /** 第一步：上传文档并解析预览分片（不落库） */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return success(documentService.uploadAndPreview(file));
    }

    /** 第三步：确认分片后向量化（MinIO + 落库 + 向量库） */
    @PostMapping(value = "/vectorize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<Map<String, Object>> vectorize(@RequestParam("file") MultipartFile file,
                                                       @RequestParam("chunks") String chunksJson,
                                                       @RequestParam("spaceId") Long spaceId) {
        return success(documentService.vectorize(file, chunksJson, spaceId));
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        documentService.deleteDocument(id);
        return success(true);
    }

}
