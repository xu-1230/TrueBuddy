package org.example.textflow.repository;

import org.example.textflow.model.WorkflowConstants;
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
            "id, name, description, schema_json, status, visibility, creator, create_time, update_time";

    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM workflow "
                    + "WHERE deleted = FALSE AND id = CAST(? AS BIGINT)";

    /** 登录用户的可见列表：自己创建的 + builtin 模板（ADR 0006：builtin 按 id 豁免） */
    private static final String SELECT_LIST_BY_OWNER =
            "SELECT " + COLUMNS + " FROM workflow "
                    + "WHERE deleted = FALSE AND (creator = CAST(? AS BIGINT) OR id = CAST("
                    + WorkflowConstants.BUILTIN_WORKFLOW_ID + " AS BIGINT)) "
                    + "ORDER BY update_time DESC, id DESC";

    /** 匿名（未登录）可见列表：/workflow/** 已要求登录（ADR 0007），此分支仅保留 Service 兜底 */
    private static final String SELECT_LIST_BUILTIN_ONLY =
            "SELECT " + COLUMNS + " FROM workflow "
                    + "WHERE deleted = FALSE AND id = CAST(" + WorkflowConstants.BUILTIN_WORKFLOW_ID + " AS BIGINT) "
                    + "ORDER BY update_time DESC, id DESC";

    /** 他人公有列表（登录用户）：visibility=1 且创建者不是当前用户；creator 为 NULL 的存量行不展示 */
    private static final String SELECT_LIST_PUBLIC_LOGGED_IN =
            "SELECT w.id, w.name, w.description, w.schema_json, w.status, w.visibility, w.creator, "
                    + "w.create_time, w.update_time, "
                    + "COALESCE(u.nickname, u.username, '未知用户') AS creator_name "
                    + "FROM workflow w "
                    + "LEFT JOIN \"system_user\" u ON u.id = w.creator "
                    + "WHERE w.deleted = FALSE AND w.visibility = 1 AND w.creator <> CAST(? AS BIGINT) "
                    + "ORDER BY w.update_time DESC, w.id DESC";

    /** 他人公有列表（匿名）：免登录白名单里没有「我的」，全部公有行都算他人的 */
    private static final String SELECT_LIST_PUBLIC_ANONYMOUS =
            "SELECT w.id, w.name, w.description, w.schema_json, w.status, w.visibility, w.creator, "
                    + "w.create_time, w.update_time, "
                    + "COALESCE(u.nickname, u.username, '未知用户') AS creator_name "
                    + "FROM workflow w "
                    + "LEFT JOIN \"system_user\" u ON u.id = w.creator "
                    + "WHERE w.deleted = FALSE AND w.visibility = 1 "
                    + "ORDER BY w.update_time DESC, w.id DESC";

    private static final String UPDATE_VISIBILITY =
            "UPDATE workflow SET visibility = ?, update_time = now() "
                    + "WHERE deleted = FALSE AND id = CAST(? AS BIGINT)";

    /** 按名称查 id（fork 命名判重用）；id 在库里是 BIGINT，读侧转成字符串与 DO 口径一致 */
    private static final String SELECT_ID_BY_NAME =
            "SELECT CAST(id AS VARCHAR) FROM workflow WHERE deleted = FALSE AND name = ? LIMIT 1";

    private static final String INSERT =
            "INSERT INTO workflow (id, name, description, schema_json, status, visibility, creator, create_time, update_time, deleted) "
                    + "VALUES (CAST(? AS BIGINT), ?, ?, ?, ?, ?, CAST(? AS BIGINT), now(), now(), FALSE)";

    private static final String UPDATE =
            "UPDATE workflow SET name = ?, description = ?, schema_json = ?, status = ?, update_time = now() "
                    + "WHERE deleted = FALSE AND id = CAST(? AS BIGINT)";

    /** 只改名称（重命名），不触碰画布定义 */
    private static final String UPDATE_NAME =
            "UPDATE workflow SET name = ?, update_time = now() "
                    + "WHERE deleted = FALSE AND id = CAST(? AS BIGINT)";

    /** 软删除：只翻 deleted 标记，会话 / 执行记录不级联 */
    private static final String SOFT_DELETE =
            "UPDATE workflow SET deleted = TRUE, update_time = now() "
                    + "WHERE deleted = FALSE AND id = CAST(? AS BIGINT)";

    private static final String COUNT_NOT_DELETED =
            "SELECT COUNT(*) FROM workflow WHERE deleted = FALSE";

    /**
     * 内置模板的幂等写入：按固定 id upsert，软删的行会被恢复。
     * 内置模板在 UI 层不可保存修改，因此用 seed 覆盖是安全的；非内置行完全不受影响。
     */
    private static final String UPSERT_BUILTIN =
            "INSERT INTO workflow (id, name, description, schema_json, status, visibility, create_time, update_time, deleted) "
                    + "VALUES (CAST(? AS BIGINT), ?, ?, ?, 0, 0, now(), now(), FALSE) "
                    + "ON CONFLICT (id) DO UPDATE SET "
                    + "name = EXCLUDED.name, description = EXCLUDED.description, "
                    + "schema_json = EXCLUDED.schema_json, status = 0, visibility = 0, "
                    + "deleted = FALSE, update_time = now()";

    private static final RowMapper<WorkflowDO> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowDO workflow = new WorkflowDO();
        workflow.setId(rs.getString("id"));
        workflow.setName(rs.getString("name"));
        workflow.setDescription(rs.getString("description"));
        workflow.setSchemaJson(rs.getString("schema_json"));
        workflow.setStatus(rs.getInt("status"));
        workflow.setVisibility(rs.getInt("visibility"));
        long creator = rs.getLong("creator");
        workflow.setCreator(rs.wasNull() ? null : creator);
        workflow.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        workflow.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        return workflow;
    };

    /** 公有列表行多带一列 creator_name（COALESCE 出展示名），其余与主 ROW_MAPPER 一致 */
    private static final RowMapper<WorkflowDO> PUBLIC_ROW_MAPPER = (rs, rowNum) -> {
        WorkflowDO workflow = ROW_MAPPER.mapRow(rs, rowNum);
        workflow.setCreatorName(rs.getString("creator_name"));
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

    /**
     * 可见列表：登录用户拿到「我的 + builtin」，匿名只拿 builtin。
     *
     * <p>{@code /workflow/**} 自 ADR 0007 起要求登录；过滤仍在查询侧完成，避免把归属判断散到 Service。
     */
    public List<WorkflowDO> selectListVisible(Long creatorId) {
        if (creatorId == null) {
            return jdbcTemplate.query(SELECT_LIST_BUILTIN_ONLY, ROW_MAPPER);
        }
        return jdbcTemplate.query(SELECT_LIST_BY_OWNER, ROW_MAPPER, creatorId);
    }

    /** 他人公有列表（visibility=1 且非本人创建；匿名时没有「本人」，返回全部公有行） */
    public List<WorkflowDO> selectListPublic(Long creatorId) {
        if (creatorId == null) {
            return jdbcTemplate.query(SELECT_LIST_PUBLIC_ANONYMOUS, PUBLIC_ROW_MAPPER);
        }
        return jdbcTemplate.query(SELECT_LIST_PUBLIC_LOGGED_IN, PUBLIC_ROW_MAPPER, creatorId);
    }

    /** 只改可见性，返回影响行数（0 表示记录不存在） */
    public int updateVisibility(String id, Integer visibility) {
        return jdbcTemplate.update(UPDATE_VISIBILITY, visibility, id);
    }

    /** 按名称查一条未删除工作流的 id（fork 命名判重用）；不存在返回 empty */
    public Optional<String> selectIdByName(String name) {
        return jdbcTemplate.queryForList(SELECT_ID_BY_NAME, String.class, name).stream().findFirst();
    }

    /** 新增一条工作流，返回影响行数 */
    public int insert(WorkflowDO workflow) {
        return jdbcTemplate.update(INSERT,
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getSchemaJson(),
                workflow.getStatus(),
                workflow.getVisibility(),
                workflow.getCreator());
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

    /** 按主键重命名一条未删除的工作流，返回影响行数（0 表示记录不存在） */
    public int updateName(String id, String name) {
        return jdbcTemplate.update(UPDATE_NAME, name, id);
    }

    /** 按主键软删除一条未删除的工作流，返回影响行数（0 表示记录不存在） */
    public int softDelete(String id) {
        return jdbcTemplate.update(SOFT_DELETE, id);
    }

    /** 未删除的工作流条数（启动初始化判空用） */
    public long countNotDeleted() {
        Long count = jdbcTemplate.queryForObject(COUNT_NOT_DELETED, Long.class);
        return count == null ? 0L : count;
    }

    /** 按固定 id 写入 / 刷新内置模板（软删行恢复；非内置行不受影响） */
    public int upsertBuiltin(String id, String name, String description, String schemaJson) {
        return jdbcTemplate.update(UPSERT_BUILTIN, id, name, description, schemaJson);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

}
