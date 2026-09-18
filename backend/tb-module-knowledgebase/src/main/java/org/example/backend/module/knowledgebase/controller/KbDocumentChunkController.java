package org.example.backend.module.knowledgebase.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.knowledgebase.model.vo.ChunkRespVO;
import org.example.backend.module.knowledgebase.service.KbDocumentChunkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 知识库文档分片接口
 */
@RestController
@RequestMapping("/knowledge-base/chunk")
public class KbDocumentChunkController {

    @Resource
    private KbDocumentChunkService chunkService;

    @GetMapping("/list")
    public CommonResult<List<ChunkRespVO>> list(@RequestParam("documentId") Long documentId) {
        return success(chunkService.getChunkList(documentId));
    }

}
