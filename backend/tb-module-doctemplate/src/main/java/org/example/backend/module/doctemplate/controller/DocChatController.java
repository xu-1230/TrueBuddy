package org.example.backend.module.doctemplate.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.doctemplate.model.vo.ChatEditReqVO;
import org.example.backend.module.doctemplate.model.vo.ChatMessageRespVO;
import org.example.backend.module.doctemplate.model.vo.ChatUndoReqVO;
import org.example.backend.module.doctemplate.model.vo.ChatUndoRespVO;
import org.example.backend.module.doctemplate.service.DocChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 文档 AI 编辑接口（前缀 /api/doc-template/chat）。
 * <p>
 * 需登录：文档模板模块已从免登录白名单移除，生成 / 历史 / 详情 / 编辑 / 撤回
 * 都按 creator 隔离。
 */
@RestController
@RequestMapping("/doc-template/chat")
public class DocChatController {

    @Resource
    private DocChatService docChatService;

    /** 一轮编辑：SSE；事件 status / assistant / version / error / done */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatEditReqVO reqVO) {
        return docChatService.chat(reqVO);
    }

    /** 撤回某次编辑：目标版本及后代失效，当前版本回到父版本 */
    @PostMapping("/undo")
    public CommonResult<ChatUndoRespVO> undo(@RequestBody ChatUndoReqVO reqVO) {
        return success(docChatService.undo(reqVO));
    }

    /** 某工作区的聊天记录（登录；按根文件 creator 隔离） */
    @GetMapping("/history")
    public CommonResult<List<ChatMessageRespVO>> history(@RequestParam Long rootFileId,
                                                         @RequestParam(required = false) Integer limit) {
        return success(docChatService.getHistory(rootFileId, limit));
    }

}
