package org.example.textflow.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.textflow.model.vo.ChatReqVO;
import org.example.textflow.model.vo.ConversationCreateReqVO;
import org.example.textflow.model.vo.ConversationVO;
import org.example.textflow.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 工作流对话执行入口：一个 {@code /chat} 取代原先的 {@code /workflow/run} 与 {@code /workflow/resume}。
 *
 * <table>
 *   <caption>接口清单</caption>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>/chat</td><td>SSE；首轮与续跑共用，由服务端按执行实例状态分流</td></tr>
 *   <tr><td>POST</td><td>/chat/conversation</td><td>新建会话（绑定已落库的工作流）</td></tr>
 *   <tr><td>GET</td><td>/chat/conversation/list</td><td>会话列表（按最近活跃倒序）</td></tr>
 * </table>
 *
 * <p><b>鉴权</b>：{@code /chat/**} **不在** {@code tb.security.permit-all-urls} 白名单里，
 * 因此要求登录——会话与执行实例都要记 {@code creator}，会话列表也要按用户隔离。
 * （这也是它没有挂在 {@code /workflow} 前缀下的原因：{@code /workflow/**} 是免登录的。）
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    /** 执行一轮交互：请求体见 {@link ChatReqVO}，事件流协议见 {@code ChatProtocol} */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatReqVO reqVO) {
        return chatService.chat(reqVO);
    }

    /** 新建会话：{@code workflow_id} 必须已落库（画布定义不内联提交） */
    @PostMapping("/conversation")
    public CommonResult<ConversationVO> createConversation(@RequestBody ConversationCreateReqVO reqVO) {
        return success(chatService.createConversation(reqVO));
    }

    /** 会话列表 */
    @GetMapping("/conversation/list")
    public CommonResult<List<ConversationVO>> listConversations() {
        return success(chatService.listConversations());
    }

}
