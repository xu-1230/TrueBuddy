package org.example.textflow.repository;

import org.example.textflow.model.WorkflowConversationDO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工作流会话仓储：{@code workflow_conversation} 表的单表读写。
 *
 * <p>口径与 {@link WorkflowDefinitionRepository} 一致（JdbcTemplate、{@code deleted = FALSE}、
 * {@code CAST(? AS BIGINT)}）。会话编号唯一，因此「建会话」的幂等由数据库唯一索引
 * {@code uk_workflow_conversation_id} 兜底——调用方重复提交同一个 {@code conversation_id}
 * 只会命中 0 行，不产生脏数据。
 */
@Repository
public class WorkflowConversationRepository {

    private static final String COLUMNS =
            "id, conversation_id, workflow_id, workflow_name, title, create_time, update_time, creator";

    private static final String SELECT_BY_CONVERSATION_ID =
            "SELECT " + COLUMNS + " FROM workflow_conversation "
                    + "WHERE deleted = FALSE AND conversation_id = ?";

    /** 会话列表：按最近活跃（每次新建执行时 touch）倒序 */
    private static final String SELECT_LIST =
            "SELECT " + COLUMNS + " FROM workflow_conversation "
                    + "WHERE deleted = FALSE ORDER BY update_time DESC, id DESC";

    private static final String INSERT =
            "INSERT INTO workflow_conversation "
                    + "(id, conversation_id, workflow_id, workflow_name, title, create_time, update_time, creator, deleted) "
                    + "VALUES (CAST(? AS BIGINT), ?, CAST(? AS BIGINT), ?, ?, now(), now(), ?, FALSE)";

    private static final String TOUCH =
            "UPDATE workflow_conversation SET update_time = now() "
                    + "WHERE deleted = FALSE AND conversation_id = ?";

    private static final RowMapper<WorkflowConversationDO> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowConversationDO conversation = new WorkflowConversationDO();
        conversation.setId(rs.getString("id"));
        conversation.setConversationId(rs.getString("conversation_id"));
        conversation.setWorkflowId(rs.getString("workflow_id"));
        conversation.setWorkflowName(rs.getString("workflow_name"));
        conversation.setTitle(rs.getString("title"));
        conversation.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        conversation.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        conversation.setCreator(nullableLong(rs, "creator"));
        return conversation;
    };

    private final JdbcTemplate jdbcTemplate;

    public WorkflowConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 新增一条会话，返回影响行数（conversation_id 已存在时返回 0） */
    public int insert(WorkflowConversationDO conversation) {
        return jdbcTemplate.update(INSERT,
                conversation.getId(),
                conversation.getConversationId(),
                conversation.getWorkflowId(),
                conversation.getWorkflowName(),
                conversation.getTitle(),
                conversation.getCreator());
    }

    /** 按会话编号查 */
    public Optional<WorkflowConversationDO> selectByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_BY_CONVERSATION_ID, ROW_MAPPER, conversationId).stream().findFirst();
    }

    /** 会话列表：按最近活跃倒序 */
    public List<WorkflowConversationDO> selectListOrderByUpdateTime() {
        return jdbcTemplate.query(SELECT_LIST, ROW_MAPPER);
    }

    /** 新建一轮交互时刷新活跃时间，使会话列表按最近使用排序 */
    public int touch(String conversationId) {
        return jdbcTemplate.update(TOUCH, conversationId);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

}
