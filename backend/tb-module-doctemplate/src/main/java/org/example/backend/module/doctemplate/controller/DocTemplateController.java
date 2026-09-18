package org.example.backend.module.doctemplate.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.doctemplate.model.vo.GenerateReqVO;
import org.example.backend.module.doctemplate.model.vo.GenerationHistoryRespVO;
import org.example.backend.module.doctemplate.model.vo.GenerationRespVO;
import org.example.backend.module.doctemplate.service.DocTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 文档模板生成接口（前缀 /api/doc-template）
 */
@RestController
@RequestMapping("/doc-template")
public class DocTemplateController {

    @Resource
    private DocTemplateService docTemplateService;

    /** 生成文档：接收模板文件 id、知识库空间、描述说明，返回生成文件列表 */
    @PostMapping("/generate")
    public CommonResult<GenerationRespVO> generate(@RequestBody GenerateReqVO reqVO) {
        return success(docTemplateService.generate(reqVO));
    }

    /** 生成历史记录 */
    @GetMapping("/history")
    public CommonResult<List<GenerationHistoryRespVO>> history(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return success(docTemplateService.getHistory(limit == null ? 0 : limit));
    }

    /** 生成详情（任务 + 文件列表） */
    @GetMapping("/detail")
    public CommonResult<GenerationRespVO> detail(@RequestParam("id") Long id) {
        return success(docTemplateService.getDetail(id));
    }

}
