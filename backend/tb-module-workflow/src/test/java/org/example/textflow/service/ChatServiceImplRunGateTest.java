package org.example.textflow.service;

import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.textflow.model.WorkflowConstants;
import org.example.textflow.model.WorkflowConversationDO;
import org.example.textflow.model.WorkflowDO;
import org.example.textflow.model.vo.ChatReqVO;
import org.example.textflow.model.vo.ConversationCreateReqVO;
import org.example.textflow.model.vo.ConversationVO;
import org.example.textflow.repository.WorkflowConversationRepository;
import org.example.textflow.repository.WorkflowDefinitionRepository;
import org.example.textflow.repository.WorkflowRunRepository;
import org.example.textflow.tool.WorkflowToolCallbacks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流直跑闸的 Service 接缝锁定（US21 / ADR 0006 决策 4）。
 *
 * <p>执行只放行「自有 + builtin」；他人的公有流必须先 fork，公有不豁免——
 * 闸门同时锁在两个入口上：建会话（POST /chat/conversation）与执行（POST /chat，
 * 兜住旧会话的续跑与凭 id 直调）。
 *
 * <p>不启动 Spring、不碰数据库；登录上下文用 mockStatic 注入。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplRunGateTest {

    private static final String BUILTIN_ID = WorkflowConstants.BUILTIN_WORKFLOW_ID;
    private static final String WORKFLOW_ID = "201";
    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 8L;
    private static final String CONVERSATION_ID = "conv-1";

    @Mock
    private WorkflowService workflowService;
    @Mock
    private WorkflowDefinitionRepository workflowRepository;
    @Mock
    private WorkflowConversationRepository conversationRepository;
    @Mock
    private WorkflowRunRepository runRepository;
    @Mock
    private ChatClient nodeChatClient;
    @Mock
    private WorkflowToolCallbacks workflowToolCallbacks;

    private ChatServiceImpl chatService;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(workflowService, workflowRepository,
                conversationRepository, runRepository, nodeChatClient, workflowToolCallbacks);
        security = mockStatic(SecurityFrameworkUtils.class);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private void loginAs(Long userId) {
        security.when(SecurityFrameworkUtils::getLoginUser)
                .thenReturn(new LoginUser().setId(userId));
    }

    private WorkflowDO workflow(String id, Long creator, int visibility) {
        WorkflowDO workflow = new WorkflowDO();
        workflow.setId(id);
        workflow.setName("批量批改");
        workflow.setVisibility(visibility);
        workflow.setCreator(creator);
        return workflow;
    }

    private WorkflowConversationDO conversationOf(String workflowId) {
        WorkflowConversationDO conversation = new WorkflowConversationDO();
        conversation.setConversationId(CONVERSATION_ID);
        conversation.setWorkflowId(workflowId);
        return conversation;
    }

    private ConversationCreateReqVO createReq() {
        ConversationCreateReqVO reqVO = new ConversationCreateReqVO();
        reqVO.setWorkflowId(WORKFLOW_ID);
        return reqVO;
    }

    @Test
    @DisplayName("建会话：自己的工作流放行")
    void createConversation_ownWorkflow_allowed() {
        loginAs(OWNER_ID);
        when(workflowRepository.selectById(WORKFLOW_ID))
                .thenReturn(Optional.of(workflow(WORKFLOW_ID, OWNER_ID, 0)));

        ConversationVO vo = chatService.createConversation(createReq());

        assertThat(vo).isNotNull();
        verify(conversationRepository).insert(any(WorkflowConversationDO.class));
    }

    @Test
    @DisplayName("建会话：builtin 豁免，任何账号可跑")
    void createConversation_builtin_allowed() {
        loginAs(OTHER_ID);
        when(workflowRepository.selectById(BUILTIN_ID))
                .thenReturn(Optional.of(workflow(BUILTIN_ID, 1L, 0)));

        ConversationCreateReqVO reqVO = new ConversationCreateReqVO();
        reqVO.setWorkflowId(BUILTIN_ID);
        ConversationVO vo = chatService.createConversation(reqVO);

        assertThat(vo).isNotNull();
        verify(conversationRepository).insert(any(WorkflowConversationDO.class));
    }

    @Test
    @DisplayName("建会话：他人公有工作流拒绝——公有 ≠ 可运行，唯一通道是 fork")
    void createConversation_othersPublic_rejected() {
        loginAs(OTHER_ID);
        when(workflowRepository.selectById(WORKFLOW_ID))
                .thenReturn(Optional.of(workflow(WORKFLOW_ID, OWNER_ID, 1)));

        assertThatThrownBy(() -> chatService.createConversation(createReq()))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));

        verify(conversationRepository, never()).insert(any());
    }

    @Test
    @DisplayName("建会话：他人私有工作流拒绝")
    void createConversation_othersPrivate_rejected() {
        loginAs(OTHER_ID);
        when(workflowRepository.selectById(WORKFLOW_ID))
                .thenReturn(Optional.of(workflow(WORKFLOW_ID, OWNER_ID, 0)));

        assertThatThrownBy(() -> chatService.createConversation(createReq()))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));

        verify(conversationRepository, never()).insert(any());
    }

    @Test
    @DisplayName("执行：他人公有工作流的既有会话同样拒绝（兜住绕过建会话的直调）")
    void chat_othersPublicConversation_rejected() {
        loginAs(OTHER_ID);
        when(conversationRepository.selectByConversationId(CONVERSATION_ID))
                .thenReturn(Optional.of(conversationOf(WORKFLOW_ID)));
        when(workflowRepository.selectById(WORKFLOW_ID))
                .thenReturn(Optional.of(workflow(WORKFLOW_ID, OWNER_ID, 1)));

        ChatReqVO reqVO = new ChatReqVO();
        reqVO.setConversationId(CONVERSATION_ID);

        assertThatThrownBy(() -> chatService.chat(reqVO))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));

        // 闸门必须在任何执行准备（读定义 / 起执行实例）之前生效
        verify(workflowService, never()).getProcess(any());
        verify(runRepository, never()).insert(any());
    }
}
