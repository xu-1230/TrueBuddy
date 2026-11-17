package org.example.backend.module.doctemplate.service;

import org.example.backend.module.doctemplate.model.vo.ChatEditReqVO;
import org.example.backend.module.doctemplate.model.vo.ChatMessageRespVO;
import org.example.backend.module.doctemplate.model.vo.ChatUndoReqVO;
import org.example.backend.module.doctemplate.model.vo.ChatUndoRespVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 文档 AI 编辑：SSE 对话编辑 + 版本撤回。
 */
public interface DocChatService {

    /** SSE 执行一轮编辑；阶段事件见接口文档 */
    SseEmitter chat(ChatEditReqVO reqVO);

    /** 撤回某次编辑：目标版本及后代失效，当前版本回到目标版本的父版本 */
    ChatUndoRespVO undo(ChatUndoReqVO reqVO);

    /** 某工作区的聊天记录（按时间正序，limit 默认 200、上限 500） */
    List<ChatMessageRespVO> getHistory(Long rootFileId, Integer limit);

}
