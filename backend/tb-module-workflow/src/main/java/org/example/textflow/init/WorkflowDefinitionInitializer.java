package org.example.textflow.init;

import org.example.textflow.entity.WorkflowProcessLoader;
import org.example.textflow.model.WorkflowConstants;
import org.example.textflow.model.WorkflowDO;
import org.example.textflow.repository.WorkflowDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 内置模板初始化：把「标准批改作业」写进 {@code workflow} 表，使工作流下拉开箱就有一个可选项。
 *
 * <p>触发条件是「表内没有任何未删除记录」——即首次部署（或表被清空）时写入一次。
 * 只要表里已有记录（含用户自建的工作流），就跳过，因此**幂等**，重启不会重复插入、更不会覆盖用户数据。
 *
 * <p>记录使用固定 id {@link WorkflowConstants#BUILTIN_WORKFLOW_ID}，服务层据此把该条标记为
 * {@code builtin}，前端下拉沿用「批改工作流 / 用户自定义工作流」的分组展示。
 */
@Component
public class WorkflowDefinitionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionInitializer.class);

    /** 内置模板名称 */
    private static final String BUILTIN_NAME = "标准批改作业";

    /** 内置模板说明 */
    private static final String BUILTIN_DESCRIPTION =
            "系统内置的批改工作流：开始 → 学生名单与批改方案规划 → 教师确认名单与评分标准"
                    + " → 逐学生批改（评分 + 评语）→ 生成成绩单与统计 → 结束";

    private final WorkflowDefinitionRepository workflowRepository;

    public WorkflowDefinitionInitializer(WorkflowDefinitionRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (workflowRepository.countNotDeleted() > 0) {
                return;
            }
            String schemaJson = readSeed();
            // 先校验种子定义合法（连线 id 非空唯一），避免把坏数据写进库
            WorkflowProcessLoader.parse(schemaJson);

            WorkflowDO workflow = new WorkflowDO();
            workflow.setId(WorkflowConstants.BUILTIN_WORKFLOW_ID);
            workflow.setName(BUILTIN_NAME);
            workflow.setDescription(BUILTIN_DESCRIPTION);
            workflow.setSchemaJson(schemaJson);
            workflow.setStatus(WorkflowConstants.STATUS_DRAFT);
            workflow.setVisibility(0);
            workflowRepository.insert(workflow);
            log.info("[WorkflowDefinitionInitializer] 已写入内置工作流模板，id={}", WorkflowConstants.BUILTIN_WORKFLOW_ID);
        } catch (Exception e) {
            // 初始化失败不阻断应用启动：执行 / 保存链路都不依赖这条种子记录
            log.warn("[WorkflowDefinitionInitializer] 内置工作流模板初始化失败: {}", e.getMessage());
        }
    }

    /** 读取 classpath 下的种子定义（原样作为 schema_json 字符串入库） */
    private String readSeed() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(WorkflowConstants.BUILTIN_SEED_LOCATION)) {
            if (in == null) {
                throw new IOException("classpath 下未找到内置模板定义: " + WorkflowConstants.BUILTIN_SEED_LOCATION);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
