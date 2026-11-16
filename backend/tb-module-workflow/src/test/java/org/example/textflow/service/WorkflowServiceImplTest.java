package org.example.textflow.service;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.textflow.model.WorkflowConstants;
import org.example.textflow.model.WorkflowDO;
import org.example.textflow.model.vo.WorkflowDetailRespVO;
import org.example.textflow.model.vo.WorkflowSaveReqVO;
import org.example.textflow.model.vo.WorkflowVO;
import org.example.textflow.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流归属地基的 Service 接缝锁定（ADR 0006）。
 *
 * <p>为什么锁在这一层：{@code /workflow/**} 在免登录白名单里，URL 层拦不住跨账号读写，
 * 「列表只暴露我的 + builtin、写操作登录 + 归属校验」这条口径必须在这里锁死。
 *
 * <p>登录上下文用 {@code mockStatic(SecurityFrameworkUtils)} 注入，不启动 Spring、不碰数据库。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    /** 最小合法画布定义：parse 只要求 JSON 可反序列化 + 边 id 非空唯一 */
    private static final String SCHEMA_JSON = "{\"nodes\":[],\"edges\":[]}";
    private static final String BUILTIN_ID = WorkflowConstants.BUILTIN_WORKFLOW_ID;
    private static final Long USER_ID = 7L;
    private static final Long OTHER_ID = 8L;

    @Mock
    private WorkflowDefinitionRepository workflowRepository;

    private WorkflowServiceImpl workflowService;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowServiceImpl(workflowRepository);
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

    private WorkflowDO workflow(String id, Long creator) {
        return workflow(id, creator, 0);
    }

    private WorkflowDO workflow(String id, Long creator, int visibility) {
        WorkflowDO workflow = new WorkflowDO();
        workflow.setId(id);
        workflow.setName("批量批改");
        workflow.setSchemaJson(SCHEMA_JSON);
        workflow.setStatus(WorkflowConstants.STATUS_DRAFT);
        workflow.setVisibility(visibility);
        workflow.setCreator(creator);
        return workflow;
    }

    private WorkflowSaveReqVO saveReq(String id) {
        WorkflowSaveReqVO reqVO = new WorkflowSaveReqVO();
        reqVO.setId(id);
        reqVO.setName("批量批改");
        reqVO.setSchemaJson(SCHEMA_JSON);
        return reqVO;
    }

    @Test
    @DisplayName("列表：登录用户按「我的 + builtin」口径查询")
    void listWorkflows_loggedIn_queriesOwnerScopedList() {
        loginAs(USER_ID);
        when(workflowRepository.selectListVisible(USER_ID)).thenReturn(List.of(workflow("201", USER_ID)));

        List<WorkflowVO> result = workflowService.listWorkflows();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("201");
        assertThat(result.get(0).getBuiltin()).isFalse();
    }

    @Test
    @DisplayName("列表：匿名（免登录白名单）只查 builtin")
    void listWorkflows_anonymous_queriesBuiltinOnly() {
        when(workflowRepository.selectListVisible(null)).thenReturn(List.of(workflow(BUILTIN_ID, 1L)));

        List<WorkflowVO> result = workflowService.listWorkflows();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBuiltin()).isTrue();
    }

    @Test
    @DisplayName("保存：匿名请求按 401 拒绝，不触发任何写库")
    void saveWorkflow_anonymous_rejectedAsUnauthorized() {
        assertThatThrownBy(() -> workflowService.saveWorkflow(saveReq(null)))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(401));

        verify(workflowRepository, never()).insert(any());
        verify(workflowRepository, never()).update(any());
    }

    @Test
    @DisplayName("保存：新建写入创建者与私有可见性")
    void saveWorkflow_create_persistsCreatorAndPrivateVisibility() {
        loginAs(USER_ID);

        workflowService.saveWorkflow(saveReq(null));

        ArgumentCaptor<WorkflowDO> captor = ArgumentCaptor.forClass(WorkflowDO.class);
        verify(workflowRepository).insert(captor.capture());
        assertThat(captor.getValue().getCreator()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getVisibility()).isZero();
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowConstants.STATUS_DRAFT);
    }

    @Test
    @DisplayName("保存：更新他人工作流按 403 拒绝")
    void saveWorkflow_updateOthers_throwsForbidden() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", OTHER_ID)));

        assertThatThrownBy(() -> workflowService.saveWorkflow(saveReq("201")))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(403));

        verify(workflowRepository, never()).update(any());
    }

    @Test
    @DisplayName("保存：内置模板任何账号都不可改写")
    void saveWorkflow_updateBuiltin_throwsForbidden() {
        loginAs(USER_ID);

        assertThatThrownBy(() -> workflowService.saveWorkflow(saveReq(BUILTIN_ID)))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(403));

        verify(workflowRepository, never()).selectById(any());
        verify(workflowRepository, never()).update(any());
    }

    @Test
    @DisplayName("保存：本人更新正常走更新通道")
    void saveWorkflow_updateOwn_updates() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", USER_ID)));

        workflowService.saveWorkflow(saveReq("201"));

        verify(workflowRepository).update(any(WorkflowDO.class));
    }

    @Test
    @DisplayName("详情：他人私有工作流按 403 拒绝")
    void getWorkflowDetail_othersPrivate_throwsForbidden() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", OTHER_ID)));

        assertThatThrownBy(() -> workflowService.getWorkflowDetail("201"))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("详情：builtin 对匿名也可见")
    void getWorkflowDetail_builtin_visibleForAnonymous() {
        when(workflowRepository.selectById(BUILTIN_ID)).thenReturn(Optional.of(workflow(BUILTIN_ID, 1L)));

        WorkflowDetailRespVO respVO = workflowService.getWorkflowDetail(BUILTIN_ID);

        assertThat(respVO.getBuiltin()).isTrue();
        assertThat(respVO.getId()).isEqualTo(BUILTIN_ID);
    }

    @Test
    @DisplayName("详情：本人可见自己的工作流")
    void getWorkflowDetail_own_visible() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", USER_ID)));

        WorkflowDetailRespVO respVO = workflowService.getWorkflowDetail("201");

        assertThat(respVO.getId()).isEqualTo("201");
        assertThat(respVO.getBuiltin()).isFalse();
        // 详情带可见性与归属标识：画布页据此呈现「公有/私有」切换与只读形态
        assertThat(respVO.getVisibility()).isZero();
        assertThat(respVO.getMine()).isTrue();
    }

    @Test
    @DisplayName("详情：他人公有工作流可读（只读画布的入口），mine=false")
    void getWorkflowDetail_othersPublic_readable() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", OTHER_ID, 1)));

        WorkflowDetailRespVO respVO = workflowService.getWorkflowDetail("201");

        assertThat(respVO.getId()).isEqualTo("201");
        assertThat(respVO.getVisibility()).isEqualTo(1);
        assertThat(respVO.getMine()).isFalse();
    }

    @Test
    @DisplayName("可见性切换：创建者本人可通过")
    void updateVisibility_owner_updates() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", USER_ID)));

        workflowService.updateVisibility("201", 1);

        verify(workflowRepository).updateVisibility("201", 1);
    }

    @Test
    @DisplayName("可见性切换：非创建者拒绝（即使该流已公有）")
    void updateVisibility_notOwner_throwsForbidden() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", OTHER_ID, 1)));

        assertThatThrownBy(() -> workflowService.updateVisibility("201", 0))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(403));

        verify(workflowRepository, never()).updateVisibility(any(), any());
    }

    @Test
    @DisplayName("可见性切换：内置模板不可改")
    void updateVisibility_builtin_throwsForbidden() {
        loginAs(USER_ID);

        assertThatThrownBy(() -> workflowService.updateVisibility(BUILTIN_ID, 1))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(403));

        verify(workflowRepository, never()).selectById(any());
        verify(workflowRepository, never()).updateVisibility(any(), any());
    }

    @Test
    @DisplayName("可见性切换：取值只能是 0/1")
    void updateVisibility_invalidValue_badRequest() {
        loginAs(USER_ID);

        assertThatThrownBy(() -> workflowService.updateVisibility("201", 2))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        assertThatThrownBy(() -> workflowService.updateVisibility("201", null))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));

        verify(workflowRepository, never()).selectById(any());
        verify(workflowRepository, never()).updateVisibility(any(), any());
    }

    @Test
    @DisplayName("可见性切换：匿名请求按 401 拒绝")
    void updateVisibility_anonymous_throwsUnauthorized() {
        assertThatThrownBy(() -> workflowService.updateVisibility("201", 1))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(401));
    }

    @Test
    @DisplayName("公有库列表：按当前登录用户查询（排除自己的公有流由 SQL 口径保证）")
    void listPublicWorkflows_passesCurrentUser() {
        loginAs(USER_ID);
        when(workflowRepository.selectListPublic(USER_ID)).thenReturn(List.of());

        assertThat(workflowService.listPublicWorkflows()).isEmpty();

        verify(workflowRepository).selectListPublic(USER_ID);
    }

    @Test
    @DisplayName("公有库列表：匿名（免登录白名单）传 null，由查询侧兜底")
    void listPublicWorkflows_anonymous_passesNull() {
        when(workflowRepository.selectListPublic(null)).thenReturn(List.of());

        assertThat(workflowService.listPublicWorkflows()).isEmpty();
    }

    @Test
    @DisplayName("fork：他人公有工作流 → 归属自己的草稿副本，命名「原名（副本）」")
    void forkWorkflow_othersPublic_createsOwnedDraftCopy() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", OTHER_ID, 1)));
        when(workflowRepository.selectIdByName("批量批改（副本）")).thenReturn(Optional.empty());

        String copyId = workflowService.forkWorkflow("201");

        assertThat(copyId).isNotBlank();
        assertThat(copyId).isNotEqualTo("201");
        ArgumentCaptor<WorkflowDO> captor = ArgumentCaptor.forClass(WorkflowDO.class);
        verify(workflowRepository).insert(captor.capture());
        WorkflowDO copy = captor.getValue();
        assertThat(copy.getName()).isEqualTo("批量批改（副本）");
        assertThat(copy.getCreator()).isEqualTo(USER_ID);
        assertThat(copy.getVisibility()).isZero();
        assertThat(copy.getStatus()).isEqualTo(WorkflowConstants.STATUS_DRAFT);
        assertThat(copy.getSchemaJson()).isEqualTo(SCHEMA_JSON);
    }

    @Test
    @DisplayName("fork：重名时叠加序号（副本 2 / 副本 3 …）")
    void forkWorkflow_nameTaken_sufficesCopyNumber() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", OTHER_ID, 1)));
        when(workflowRepository.selectIdByName("批量批改（副本）")).thenReturn(Optional.of("999"));
        when(workflowRepository.selectIdByName("批量批改（副本 2）")).thenReturn(Optional.empty());

        workflowService.forkWorkflow("201");

        ArgumentCaptor<WorkflowDO> captor = ArgumentCaptor.forClass(WorkflowDO.class);
        verify(workflowRepository).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("批量批改（副本 2）");
    }

    @Test
    @DisplayName("fork：他人私有工作流拒绝")
    void forkWorkflow_othersPrivate_throwsForbidden() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.of(workflow("201", OTHER_ID)));

        assertThatThrownBy(() -> workflowService.forkWorkflow("201"))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(403));

        verify(workflowRepository, never()).insert(any());
    }

    @Test
    @DisplayName("fork：内置模板显式拒绝（即使它被置为公有也不放行）")
    void forkWorkflow_builtin_throwsForbidden() {
        loginAs(USER_ID);
        // visibility=1 让「他人公有」口径对 builtin 同样成立：用例锁的是 builtin 分支本身，
        // 而不是「他人私有恰好被拒」
        when(workflowRepository.selectById(BUILTIN_ID)).thenReturn(Optional.of(workflow(BUILTIN_ID, 1L, 1)));

        assertThatThrownBy(() -> workflowService.forkWorkflow(BUILTIN_ID))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(403));

        verify(workflowRepository, never()).insert(any());
        verify(workflowRepository, never()).selectIdByName(any());
    }

    @Test
    @DisplayName("fork：匿名请求按 401 拒绝")
    void forkWorkflow_anonymous_rejectedAsUnauthorized() {
        assertThatThrownBy(() -> workflowService.forkWorkflow("201"))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(401));

        verify(workflowRepository, never()).insert(any());
    }

    @Test
    @DisplayName("fork：工作流不存在按 400 拒绝")
    void forkWorkflow_notExists_badRequest() {
        loginAs(USER_ID);
        when(workflowRepository.selectById("201")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workflowService.forkWorkflow("201"))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));

        verify(workflowRepository, never()).insert(any());
    }
}
