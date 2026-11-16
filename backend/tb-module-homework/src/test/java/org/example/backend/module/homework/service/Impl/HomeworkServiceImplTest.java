package org.example.backend.module.homework.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.homework.mapper.HomeworkFileMapper;
import org.example.backend.module.homework.mapper.HomeworkGradingResultMapper;
import org.example.backend.module.homework.mapper.HomeworkTaskMapper;
import org.example.backend.module.homework.model.HomeworkFileDO;
import org.example.backend.module.homework.model.HomeworkGradingResultDO;
import org.example.backend.module.homework.model.HomeworkTaskDO;
import org.example.backend.module.homework.model.vo.HomeworkResultRecordVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteReqVO;
import org.example.backend.module.homework.model.vo.HomeworkResultWriteRespVO;
import org.example.backend.module.homework.model.vo.HomeworkTaskDetailRespVO;
import org.example.backend.module.homework.model.vo.HomeworkTaskItemVO;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批改历史与状态机的 Service 接缝锁定（003）。
 *
 * <p>锁三件事：任务列表口径（最近 N 条、limit 收口）、详情回看字段
 * （配置 + 文件树，relative_path 映射为 path）、状态机只允许 0→1。
 *
 * <p>纯 Mockito 注入 Mapper，不启动 Spring、不碰数据库。
 */
@ExtendWith(MockitoExtension.class)
class HomeworkServiceImplTest {

    private static final Long TASK_ID = 42L;

    @Mock
    private HomeworkTaskMapper taskMapper;
    @Mock
    private HomeworkFileMapper fileMapper;
    @Mock
    private HomeworkGradingResultMapper resultMapper;
    @Mock
    private InfraFileMapper infraFileMapper;
    @Mock
    private MinioFileStorageService minioFileStorageService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private HomeworkServiceImpl service;

    /** 让事务模板直接执行回调（不启动 Spring），覆盖式回写的三步都在回调里 */
    @SuppressWarnings("unchecked")
    private void runTransactionInline() {
        doAnswer((Answer<Void>) invocation -> {
            invocation.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private HomeworkTaskDO task(Integer status, String spaceIds) {
        HomeworkTaskDO task = new HomeworkTaskDO();
        task.setId(TASK_ID);
        task.setFolderName("电子技术基础-第3次作业");
        task.setStatus(status);
        task.setFileCount(3);
        task.setTotalSize(2048L);
        task.setDescription("按评分标准逐题给分");
        task.setSpaceIds(spaceIds);
        task.setCreateTime(LocalDateTime.of(2026, 11, 16, 10, 30));
        return task;
    }

    private HomeworkFileDO file(String relativePath) {
        HomeworkFileDO file = new HomeworkFileDO();
        file.setId(1L);
        file.setTaskId(TASK_ID);
        file.setFileId(9L);
        file.setRelativePath(relativePath);
        file.setName(relativePath.substring(relativePath.lastIndexOf('/') + 1));
        file.setSize(512L);
        file.setUrl("https://minio.example/signed/xxx.docx");
        file.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return file;
    }

    @Test
    @DisplayName("任务列表：未传 limit 时按默认 20 条查询")
    void getTaskList_defaultLimit() {
        when(taskMapper.selectRecentList(20)).thenReturn(List.of());

        service.getTaskList(null);

        verify(taskMapper).selectRecentList(20);
    }

    @Test
    @DisplayName("任务列表：limit 超上限时截断")
    void getTaskList_overLimitClamped() {
        when(taskMapper.selectRecentList(100)).thenReturn(List.of());

        service.getTaskList(500);

        verify(taskMapper).selectRecentList(100);
    }

    @Test
    @DisplayName("任务列表：DO → VO 字段映射，spaceIds 的 CSV 还原为列表")
    void getTaskList_mapsFieldsAndSplitsSpaceIds() {
        when(taskMapper.selectRecentList(20)).thenReturn(List.of(task(1, "1, 2,")));

        List<HomeworkTaskItemVO> result = service.getTaskList(0);

        assertThat(result).hasSize(1);
        HomeworkTaskItemVO vo = result.get(0);
        assertThat(vo.getId()).isEqualTo(TASK_ID);
        assertThat(vo.getFolderName()).isEqualTo("电子技术基础-第3次作业");
        assertThat(vo.getStatus()).isEqualTo(1);
        assertThat(vo.getFileCount()).isEqualTo(3);
        assertThat(vo.getTotalSize()).isEqualTo(2048L);
        assertThat(vo.getDescription()).isEqualTo("按评分标准逐题给分");
        assertThat(vo.getSpaceIds()).containsExactly("1", "2");
        assertThat(vo.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 11, 16, 10, 30));
    }

    @Test
    @DisplayName("任务列表：批改配置未写入时 spaceIds 归一为空列表")
    void getTaskList_blankSpaceIds_normalizedToEmptyList() {
        when(taskMapper.selectRecentList(20)).thenReturn(List.of(task(0, null)));

        List<HomeworkTaskItemVO> result = service.getTaskList(0);

        assertThat(result.get(0).getSpaceIds()).isEmpty();
    }

    @Test
    @DisplayName("详情：任务不存在按模块错误码拒绝")
    void getTaskDetail_notExists_throwsModuleErrorCode() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getTaskDetail(TASK_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_004_000_006));
    }

    @Test
    @DisplayName("详情：配置 + 文件树字段映射（relative_path → path，URL 与 MIME 带出）")
    void getTaskDetail_mapsConfigAndFiles() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(2, "1"));
        when(fileMapper.selectListWithInfraByTaskId(TASK_ID)).thenReturn(List.of(
                file("电子技术基础-第3次作业/20230101-张三/第三章习题.docx")));

        HomeworkTaskDetailRespVO detail = service.getTaskDetail(TASK_ID);

        assertThat(detail.getId()).isEqualTo(TASK_ID);
        assertThat(detail.getFolderName()).isEqualTo("电子技术基础-第3次作业");
        assertThat(detail.getStatus()).isEqualTo(2);
        assertThat(detail.getDescription()).isEqualTo("按评分标准逐题给分");
        assertThat(detail.getSpaceIds()).containsExactly("1");
        assertThat(detail.getFiles()).hasSize(1);
        assertThat(detail.getFiles().get(0).getPath())
                .isEqualTo("电子技术基础-第3次作业/20230101-张三/第三章习题.docx");
        assertThat(detail.getFiles().get(0).getName()).isEqualTo("第三章习题.docx");
        assertThat(detail.getFiles().get(0).getSize()).isEqualTo(512L);
        assertThat(detail.getFiles().get(0).getUrl()).isEqualTo("https://minio.example/signed/xxx.docx");
        assertThat(detail.getFiles().get(0).getType()).startsWith("application/vnd");
    }

    @Test
    @DisplayName("开始批改：任务不存在按模块错误码拒绝")
    void startGrading_notExists_throwsModuleErrorCode() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.startGrading(TASK_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_004_000_006));
    }

    @Test
    @DisplayName("开始批改：待批改（0）时置为批改中（1）")
    void startGrading_fromPending_setsGrading() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(0, null));
        when(taskMapper.updateStatusToGrading(TASK_ID)).thenReturn(1);

        service.startGrading(TASK_ID);

        verify(taskMapper).updateStatusToGrading(TASK_ID);
    }

    @Test
    @DisplayName("开始批改：已不在待批改状态（条件更新未命中）按模块错误码拒绝")
    void startGrading_notPending_throwsModuleErrorCode() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(2, null));
        when(taskMapper.updateStatusToGrading(TASK_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.startGrading(TASK_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_004_000_007));
    }

    // ========== 004：结果覆盖式回写与评分回看 ==========

    private HomeworkResultRecordVO recordVO(String student, Double score, List<String> deductions) {
        HomeworkResultRecordVO record = new HomeworkResultRecordVO();
        record.setStudent(student);
        record.setScore(score);
        record.setGrade("良");
        record.setComment("整体完整");
        record.setDeductions(deductions);
        record.setReadFiles(List.of("电子技术基础-第3次作业/20230101-张三/第三章习题.docx"));
        return record;
    }

    private HomeworkGradingResultDO resultRow(String student, String deductionsJson) {
        HomeworkGradingResultDO row = new HomeworkGradingResultDO();
        row.setTaskId(TASK_ID);
        row.setStudent(student);
        row.setScore(new java.math.BigDecimal("85.5"));
        row.setGrade("良");
        row.setComment("整体完整");
        row.setDeductions(deductionsJson);
        row.setReadFiles("[\"电子技术基础-第3次作业/20230101-张三/第三章习题.docx\"]");
        row.setConversationId("conv-1");
        return row;
    }

    @Test
    @DisplayName("详情：评分记录随配置一并返回，JSON 数组文本还原为列表，漏批份数得出")
    void getTaskDetail_mapsResultsAndMissedCount() {
        HomeworkTaskDO pending = task(2, "1");
        pending.setFileCount(3);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pending);
        when(fileMapper.selectListWithInfraByTaskId(TASK_ID)).thenReturn(List.of());
        when(resultMapper.selectListByTaskId(TASK_ID)).thenReturn(List.of(
                resultRow("张三", "[\"字迹潦草\"]"), resultRow("李四", "[]")));

        HomeworkTaskDetailRespVO detail = service.getTaskDetail(TASK_ID);

        assertThat(detail.getResults()).hasSize(2);
        assertThat(detail.getResults().get(0).getStudent()).isEqualTo("张三");
        assertThat(detail.getResults().get(0).getScore()).isEqualTo(85.5);
        assertThat(detail.getResults().get(0).getDeductions()).containsExactly("字迹潦草");
        assertThat(detail.getResults().get(1).getDeductions()).isEmpty();
        assertThat(detail.getMissedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("回写：任务不存在按模块错误码拒绝")
    void writeResults_taskNotExists_throwsModuleErrorCode() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.writeResults(TASK_ID, new HomeworkResultWriteReqVO()))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_004_000_006));
    }

    @Test
    @DisplayName("回写：满批收敛到「成功(2)」，旧行删除 + 整批插入 + student/JSON 文本归一")
    void writeResults_fullRun_setsSuccess() {
        runTransactionInline();
        HomeworkTaskDO pending = task(1, null);
        pending.setFileCount(2);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pending);
        when(taskMapper.updateStatusToFinal(eq(TASK_ID), eq(2))).thenReturn(1);

        HomeworkResultWriteReqVO reqVO = new HomeworkResultWriteReqVO();
        reqVO.setConversationId("conv-9");
        reqVO.setRecords(List.of(
                recordVO(" 张三 ", 88.0, List.of("字迹潦草")),
                recordVO("  ", 75.0, null)));

        HomeworkResultWriteRespVO respVO = service.writeResults(TASK_ID, reqVO);

        assertThat(respVO.getStatus()).isEqualTo(2);
        assertThat(respVO.getTotal()).isEqualTo(2);
        assertThat(respVO.getMissed()).isZero();
        verify(resultMapper).deleteByTaskId(TASK_ID);
        ArgumentCaptor<List<HomeworkGradingResultDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultMapper).insertBatch(captor.capture());
        HomeworkGradingResultDO first = captor.getValue().get(0);
        assertThat(first.getStudent()).isEqualTo("张三");
        assertThat(first.getDeductions()).isEqualTo("[\"字迹潦草\"]");
        assertThat(first.getConversationId()).isEqualTo("conv-9");
        HomeworkGradingResultDO second = captor.getValue().get(1);
        assertThat(second.getStudent()).isEqualTo("未署名");
        assertThat(second.getDeductions()).isEqualTo("[]");
        verify(taskMapper).updateStatusToFinal(TASK_ID, 2);
    }

    @Test
    @DisplayName("回写：有漏批收敛到「部分成功(3)」")
    void writeResults_partialRun_setsPartial() {
        runTransactionInline();
        HomeworkTaskDO pending = task(1, null);
        pending.setFileCount(3);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pending);
        when(taskMapper.updateStatusToFinal(eq(TASK_ID), eq(3))).thenReturn(1);

        HomeworkResultWriteReqVO reqVO = new HomeworkResultWriteReqVO();
        reqVO.setRecords(List.of(recordVO("张三", 88.0, List.of())));

        HomeworkResultWriteRespVO respVO = service.writeResults(TASK_ID, reqVO);

        assertThat(respVO.getStatus()).isEqualTo(3);
        assertThat(respVO.getMissed()).isEqualTo(2);
    }

    @Test
    @DisplayName("回写：空记录收敛到「失败(4)」，只删不插")
    void writeResults_emptyRecords_setsFailed() {
        runTransactionInline();
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(1, null));
        when(taskMapper.updateStatusToFinal(eq(TASK_ID), eq(4))).thenReturn(1);

        HomeworkResultWriteReqVO reqVO = new HomeworkResultWriteReqVO();
        reqVO.setRecords(List.of());

        HomeworkResultWriteRespVO respVO = service.writeResults(TASK_ID, reqVO);

        assertThat(respVO.getStatus()).isEqualTo(4);
        verify(resultMapper).deleteByTaskId(TASK_ID);
        verify(resultMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("回写：待批改（0）状态不可直接写终态（条件更新未命中即拒绝）")
    void writeResults_fromPending_throwsModuleErrorCode() {
        runTransactionInline();
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(0, null));
        when(taskMapper.updateStatusToFinal(eq(TASK_ID), eq(4))).thenReturn(0);

        HomeworkResultWriteReqVO reqVO = new HomeworkResultWriteReqVO();
        reqVO.setRecords(List.of());

        assertThatThrownBy(() -> service.writeResults(TASK_ID, reqVO))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(1_004_000_007));
    }

}
