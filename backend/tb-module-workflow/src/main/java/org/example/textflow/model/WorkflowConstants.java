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

    /** 执行状态：待运行（一期未使用，保留与 workflow_run 表口径一致） */
    public static final int RUN_STATUS_PENDING = 0;

    /** 执行状态：执行中 */
    public static final int RUN_STATUS_RUNNING = 1;

    /** 执行状态：成功 */
    public static final int RUN_STATUS_SUCCESS = 2;

    /** 执行状态：失败 */
    public static final int RUN_STATUS_FAILED = 3;

    /** 执行状态：中断（等待人工确认，非终态） */
    public static final int RUN_STATUS_INTERRUPTED = 4;

    /** 执行状态：已取消（一期未使用，保留） */
    public static final int RUN_STATUS_CANCELLED = 5;

    /** 判断是否为终态：终态后再次发起 /chat 才新起一条执行实例 */
    public static boolean isRunTerminal(Integer status) {
        return status != null
                && (status == RUN_STATUS_SUCCESS || status == RUN_STATUS_FAILED || status == RUN_STATUS_CANCELLED);
    }

    private WorkflowConstants() {
    }

}
