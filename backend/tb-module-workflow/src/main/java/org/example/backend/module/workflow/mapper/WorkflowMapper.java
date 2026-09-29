package org.example.backend.module.workflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.workflow.model.WorkflowDO;
import org.example.backend.module.workflow.model.vo.WorkflowVO;

import java.util.List;

/**
 * 工作流定义数据访问（注解 SQL，无 XML）。
 *
 * <p>主键由应用生成雪花 ID，故 insert 不使用自增回写。
 */
@Mapper
public interface WorkflowMapper {

    @Insert("INSERT INTO workflow (id, name, description, schema_json, status, create_time, update_time) "
            + "VALUES (#{id}, #{name}, #{description}, #{schemaJson}, #{status}, now(), now())")
    int insert(WorkflowDO workflow);

    @Select("SELECT * FROM workflow WHERE id = #{id} AND deleted = FALSE")
    WorkflowDO selectById(Long id);

    @Update("UPDATE workflow SET name = #{name}, description = #{description}, schema_json = #{schemaJson}, "
            + "status = #{status}, update_time = now() WHERE id = #{id} AND deleted = FALSE")
    int updateById(WorkflowDO workflow);

    /**
     * 工作流列表（不查 schema_json，避免把大字段拉进列表）。
     *
     * <p>直接把 id 转文本、时间转秒级时间戳，与 {@link WorkflowVO} 的字段类型对齐
     * （VO 里 workflowId 是 String、create_time / update_time 是秒级 Long）。
     */
    @Select("SELECT id::text AS workflow_id, name, status, "
            + "extract(epoch from create_time)::bigint AS create_time, "
            + "extract(epoch from update_time)::bigint AS update_time "
            + "FROM workflow WHERE deleted = FALSE ORDER BY update_time DESC")
    List<WorkflowVO> selectList();

    @Update("UPDATE workflow SET deleted = TRUE, update_time = now() WHERE id = #{id} AND deleted = FALSE")
    int deleteById(Long id);

}
