package org.example.textflow.repository;

import org.example.textflow.model.WorkflowConstants;
import org.example.textflow.model.WorkflowRunDO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 工作流执行实例仓储：{@code workflow_run} 表的单表读写。
 *
 * <p>与 {@link WorkflowDefinitionRepository} 同口径：本模块不引入 MyBatis（启动类 {@code @MapperScan}
 * 的基准包 {@code org.example.backend} 不含 {@code org.example.textflow}），直接用 {@link JdbcTemplate}；
 * 所有查询带 {@code deleted = FALSE}；{@code id} / {@code workflow_id} 在库里是 {@code BIGINT}，
 * 调用方持有的是字符串，写入侧统一 {@code CAST(? AS BIGINT)} 显式转换。
 *
 * <p>表中**不存**入参、产出与错误详情：一次执行只有「属于哪个会话、跑的哪个流程、什么状态、耗时多久」。
 * 续跑复用同一条记录（{@code status} 4 → 1），因此没有 update 定义 / 参数的方法。
 */
@Repository
public class WorkflowRunRepository {

    private static final String COLUMNS =
            "id, execute_id, conversation_id, workflow_id, workflow_name, status, "
                    + "start_time, end_time, duration, create_time, update_time, creator";

    private static final String SELECT_BY_EXECUTE_ID =
            "SELECT " + COLUMNS + " FROM workflow_run "
                    + "WHERE deleted = FALSE AND execute_id = ?";

    /** 会话内最新一条执行：/chat 据此判断「新建 / 续跑 / 拒绝并发」 */
    private static final String SELECT_LATEST_BY_CONVERSATION =
            "SELECT " + COLUMNS + " FROM workflow_run "
                    + "WHERE deleted = FALSE AND conversation_id = ? "
                    + "ORDER BY create_time DESC, id DESC LIMIT 1";

    private static final String INSERT =
            "INSERT INTO workflow_run "
                    + "(id, execute_id, conversation_id, workflow_id, workflow_name, status, "
                    + "start_time, create_time, update_time, creator, deleted) "
                    + "VALUES (CAST(? AS BIGINT), ?, ?, CAST(? AS BIGINT), ?, ?, now(), now(), now(), ?, FALSE)";

    private static final String MARK_RUNNING =
            "UPDATE workflow_run SET status = " + WorkflowConstants.RUN_STATUS_RUNNING
                    + ", start_time = now(), update_time = now() "
                    + "WHERE deleted = FALSE AND execute_id = ?";

    /**
     * 结束一次执行：耗时由数据库按 {@code start_time} 计算（毫秒），避免应用侧与库时钟不一致。
     * {@code start_time} 为空（极端情况：行已建但未标执行中）时耗时为 NULL。
     */
    private static final String MARK_FINISHED =
            "UPDATE workflow_run SET status = ?, end_time = now(), "
                    + "duration = CASE WHEN start_time IS NULL THEN NULL "
                    + "ELSE (EXTRACT(EPOCH FROM (now() - start_time)) * 1000)::BIGINT END, "
                    + "update_time = now() "
                    + "WHERE deleted = FALSE AND execute_id = ?";

    private static final RowMapper<WorkflowRunDO> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowRunDO run = new WorkflowRunDO();
        run.setId(rs.getString("id"));
        run.setExecuteId(rs.getString("execute_id"));
        run.setConversationId(rs.getString("conversation_id"));
        run.setWorkflowId(rs.getString("workflow_id"));
        run.setWorkflowName(rs.getString("workflow_name"));
        run.setStatus(rs.getInt("status"));
        run.setStartTime(toLocalDateTime(rs.getTimestamp("start_time")));
        run.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
        run.setDuration(nullableLong(rs, "duration"));
        run.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        run.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        run.setCreator(nullableLong(rs, "creator"));
        return run;
    };

    private final JdbcTemplate jdbcTemplate;

    public WorkflowRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 新增一条执行实例，返回影响行数 */
    public int insert(WorkflowRunDO run) {
        return jdbcTemplate.update(INSERT,
                run.getId(),
                run.getExecuteId(),
                run.getConversationId(),
                run.getWorkflowId(),
                run.getWorkflowName(),
                run.getStatus(),
                run.getCreator());
    }

    /** 按执行编号查（execute_id 全局唯一） */
    public Optional<WorkflowRunDO> selectByExecuteId(String executeId) {
        if (executeId == null || executeId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_BY_EXECUTE_ID, ROW_MAPPER, executeId).stream().findFirst();
    }

    /** 会话内最新一条执行（/chat 的分流依据） */
    public Optional<WorkflowRunDO> selectLatestByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_LATEST_BY_CONVERSATION, ROW_MAPPER, conversationId).stream().findFirst();
    }

    /** 标记为执行中（新建后立刻调用；续跑时把 4 改回 1 也走这里，start_time 重置为本次续跑起点） */
    public int markRunning(String executeId) {
        return jdbcTemplate.update(MARK_RUNNING, executeId);
    }

    /** 标记终态：2=成功，3=失败，4=中断（中断不是终态，但同样要结束本次 HTTP 执行） */
    public int markFinished(String executeId, int status) {
        return jdbcTemplate.update(MARK_FINISHED, status, executeId);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

}
