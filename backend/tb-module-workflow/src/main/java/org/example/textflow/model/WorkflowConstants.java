package org.example.textflow.model;

/**
 * 工作流模块的公共常量。
 *
 * <p>内置模板的 id 需要被「启动初始化器」（写入种子）与「服务层」（判定 builtin）共用，
 * 因此单独抽成常量类，避免两处各写一份字符串而漂移。
 */
public final class WorkflowConstants {

    /**
     * 内置模板「标准批改作业」的固定 id。
     *
     * <p>取雪花 ID 形态（19 位数字串）而非语义化名称，是为了与业务工作流的 id 同构——
     * 前端全程按字符串比较、可直接放进 {@code el-select} 的 value，不需要给内置项单独开一套分支。
     */
    public static final String BUILTIN_WORKFLOW_ID = "1000000000000000001";

    /** 内置模板种子定义的 classpath 位置 */
    public static final String BUILTIN_SEED_LOCATION = "workflow/standard-grading.json";

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;

    private WorkflowConstants() {
    }

}
