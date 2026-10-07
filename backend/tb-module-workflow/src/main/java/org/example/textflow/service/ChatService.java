package org.example.textflow.service;

import org.example.textflow.model.vo.ChatReqVO;
import org.example.textflow.model.vo.ConversationCreateReqVO;
import org.example.textflow.model.vo.ConversationVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 工作流对话执行服务：{@code /chat} 一个入口承载「首轮执行」与「中断续跑」。
 */
public interface ChatService {

    /**
     * 执行一轮交互，进度经 SSE 推送（协议见 {@code ChatProtocol}）。
     *
     * <p>首轮 / 续跑由服务端按该会话最新一条执行实例的状态分流，客户端不传状态；
     * 出入参见 {@link ChatReqVO}。
     */
    SseEmitter chat(ChatReqVO reqVO);

    /** 新建会话：绑定一个已落库的工作流，返回会话编号（后续 /chat 全程回传它） */
    ConversationVO createConversation(ConversationCreateReqVO reqVO);

    /** 会话列表（按最近活跃倒序） */
    List<ConversationVO> listConversations();

}
