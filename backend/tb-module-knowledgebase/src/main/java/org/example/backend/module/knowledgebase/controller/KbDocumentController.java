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
 * 知识库文档接口。
 *
 * <p>文档处理为「异步任务」模型，共三步：
 * <ol>
 *     <li>POST /upload —— 文件列表上传并提交解析任务，返回各文件的 documentId</li>
 *     <li>GET  /parse-status —— 轮询解析状态，完成后返回分片预览</li>
 *     <li>POST /vectorize —— 确认分片后向量化落库</li>
 *     <li>POST /abandon  —— 放弃未完成向量化的文档（逻辑删除）</li>
 * </ol>
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

    /** 第一步：文件列表上传并提交解析任务（MinIO 存储 + 落库 + 提交解析引擎） */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<List<Map<String, Object>>> upload(@RequestParam("files") List<MultipartFile> files,
                                                          @RequestParam("spaceId") Long spaceId) {
        return success(documentService.uploadAndSubmit(files, spaceId));
    }

    /** 第二步：轮询解析状态；解析完成后落库分片并返回分片预览 */
    @GetMapping("/parse-status")
    public CommonResult<Map<String, Object>> parseStatus(@RequestParam("documentId") Long documentId) {
        return success(documentService.getParseStatus(documentId));
    }

    /** 第三步：确认分片后向量化（覆盖落库分片 + 写入向量库） */
    @PostMapping(value = "/vectorize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<Map<String, Object>> vectorize(@RequestParam("documentId") Long documentId,
                                                       @RequestParam("spaceId") Long spaceId,
                                                       @RequestParam("chunks") String chunksJson) {
        return success(documentService.vectorize(documentId, spaceId, chunksJson));
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        documentService.deleteDocument(id);
        return success(true);
    }

    /** 放弃未确认分片的文档：逻辑删除，不再出现在列表（已完成向量化的文档不受影响） */
    @PostMapping("/abandon")
    public CommonResult<Boolean> abandon(@RequestParam("documentId") Long documentId) {
        documentService.abandonDocument(documentId);
        return success(true);
    }

}
