package org.example.textflow.repository;

import org.example.textflow.model.WorkflowDO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工作流定义仓储：{@code workflow} 表的单表读写。
 *
 * <p>本模块不引入 MyBatis（启动类的 {@code @MapperScan} 基准包是 {@code org.example.backend}，
 * 不含 {@code org.example.textflow}），而模块 {@code pom.xml} 已为「读 workflow 表」预置了
 * {@code spring-boot-starter-jdbc} + {@code postgresql}，因此这里直接用 {@link JdbcTemplate}：
 * 零新增依赖、不改扫描配置，单表 CRUD 足够。
 *
 * <p>所有查询都带 {@code deleted = FALSE}（软删）；{@code id} 在库里是 {@code BIGINT}，
 * 而调用方持有的是雪花 ID 字符串，故写入侧统一用 {@code CAST(? AS BIGINT)} 显式转换，
 * 避免驱动把参数当 {@code varchar} 绑定时 PostgreSQL 拒绝隐式转换。
 *
 * <p>列表查询同样取 {@code schema_json}：服务层要按它统计顶层节点数（{@code stepCount}，
 * 下拉里的「N 个步骤」），统计完即丢弃，**不回传**前端。列表的数据量是「人工创建的工作流条数」，
 * 这点读取成本可忽略。
 */
@Repository
public class WorkflowDefinitionRepository {

    /** 查询列：id 以字符串读出（雪花 ID 超出前端安全整数范围） */
    private static final String COLUMNS =
            "id, name, description, schema_json, status, create_time, update_time";

    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM workflow "
                    + "WHERE deleted = FALSE AND id = CAST(? AS BIGINT)";

    private static final String SELECT_LIST =
            "SELECT " + COLUMNS + " FROM workflow "
                    + "WHERE deleted = FALSE ORDER BY update_time DESC, id DESC";

    private static final String INSERT =
            "INSERT INTO workflow (id, name, description, schema_json, status, create_time, update_time, deleted) "
                    + "VALUES (CAST(? AS BIGINT), ?, ?, ?, ?, now(), now(), FALSE)";

    private static final String UPDATE =
            "UPDATE workflow SET name = ?, description = ?, schema_json = ?, status = ?, update_time = now() "
                    + "WHERE deleted = FALSE AND id = CAST(? AS BIGINT)";

    private static final String COUNT_NOT_DELETED =
            "SELECT COUNT(*) FROM workflow WHERE deleted = FALSE";

    private static final RowMapper<WorkflowDO> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowDO workflow = new WorkflowDO();
        workflow.setId(rs.getString("id"));
        workflow.setName(rs.getString("name"));
        workflow.setDescription(rs.getString("description"));
        workflow.setSchemaJson(rs.getString("schema_json"));
        workflow.setStatus(rs.getInt("status"));
        workflow.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        workflow.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        return workflow;
    };

    private final JdbcTemplate jdbcTemplate;

    public WorkflowDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按主键查一条未删除的工作流 */
    public Optional<WorkflowDO> selectById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_BY_ID, ROW_MAPPER, id).stream().findFirst();
    }

    /** 列表：按更新时间倒序 */
    public List<WorkflowDO> selectListOrderByUpdateTime() {
        return jdbcTemplate.query(SELECT_LIST, ROW_MAPPER);
    }

    /** 新增一条工作流，返回影响行数 */
    public int insert(WorkflowDO workflow) {
        return jdbcTemplate.update(INSERT,
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getSchemaJson(),
                workflow.getStatus());
    }

    /** 按主键更新一条未删除的工作流，返回影响行数（0 表示记录不存在） */
    public int update(WorkflowDO workflow) {
        return jdbcTemplate.update(UPDATE,
                workflow.getName(),
                workflow.getDescription(),
                workflow.getSchemaJson(),
                workflow.getStatus(),
                workflow.getId());
    }

    /** 未删除的工作流条数（启动初始化判空用） */
    public long countNotDeleted() {
        Long count = jdbcTemplate.queryForObject(COUNT_NOT_DELETED, Long.class);
        return count == null ? 0L : count;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

}
