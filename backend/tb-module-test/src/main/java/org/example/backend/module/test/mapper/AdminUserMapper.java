package org.example.backend.module.test.mapper;


import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.test.model.AdminUserDO;

import java.util.List;

@Mapper
public interface AdminUserMapper {

    /**
     * 管理端用户分页：keyword 模糊匹配 账号/昵称/邮箱，status 等值，role 按绑定角色过滤。
     * 分页参数由 Service 计算后传入（无 PageHelper，手写 LIMIT/OFFSET）。
     */
    @Select("<script>"
            + "select * from \"system_user\""
            + " <where>"
            + "   <if test=\"keyword != null and keyword != ''\">"
            + "     and (username ilike concat('%', #{keyword}, '%')"
            + "       or nickname ilike concat('%', #{keyword}, '%')"
            + "       or email ilike concat('%', #{keyword}, '%'))"
            + "   </if>"
            + "   <if test=\"status != null\">and status = #{status}</if>"
            + "   <if test=\"role != null and role != ''\">"
            + "     and id in (select ur.user_id from system_user_role ur"
            + "       join system_role r on r.id = ur.role_id where r.code = #{role})"
            + "   </if>"
            + " </where>"
            + " order by id"
            + " limit #{size} offset #{offset}"
            + "</script>")
    List<AdminUserDO> selectPage(@Param("keyword") String keyword, @Param("status") Integer status,
                                 @Param("role") String role, @Param("offset") int offset, @Param("size") int size);

    /** 与 {@link #selectPage} 同一套过滤条件，只取总数 */
    @Select("<script>"
            + "select count(*) from \"system_user\""
            + " <where>"
            + "   <if test=\"keyword != null and keyword != ''\">"
            + "     and (username ilike concat('%', #{keyword}, '%')"
            + "       or nickname ilike concat('%', #{keyword}, '%')"
            + "       or email ilike concat('%', #{keyword}, '%'))"
            + "   </if>"
            + "   <if test=\"status != null\">and status = #{status}</if>"
            + "   <if test=\"role != null and role != ''\">"
            + "     and id in (select ur.user_id from system_user_role ur"
            + "       join system_role r on r.id = ur.role_id where r.code = #{role})"
            + "   </if>"
            + " </where>"
            + "</script>")
    long selectCount(@Param("keyword") String keyword, @Param("status") Integer status,
                     @Param("role") String role);

    /** 管理端编辑：仅更新表单覆盖的昵称 / 邮箱 / 状态，账号与密码不在其列 */
    @Update("<script>"
            + "update \"system_user\""
            + " <set>"
            + "   <if test=\"nickname != null\">nickname = #{nickname},</if>"
            + "   <if test=\"email != null\">email = #{email},</if>"
            + "   <if test=\"status != null\">status = #{status},</if>"
            + " </set>"
            + " where id = #{id}"
            + "</script>")
    int updateById(@Param("id") Long id, @Param("nickname") String nickname,
                   @Param("email") String email, @Param("status") Integer status);

    /** 物理删除（system_user 无软删列），调用方需同事务清理角色绑定 */
    @Delete("delete from \"system_user\" where id = #{id}")
    int deleteById(Long id);

    @Select("select * from \"system_user\" where username = #{username}")
    AdminUserDO selectByUsername(String username);

    @Insert("insert into \"system_user\" (username, password, nickname, remark, email, avatar, status) "
            + "values (#{username}, #{password}, #{nickname}, #{remark}, #{email}, #{avatar}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminUserDO adminUser);

    @Update("update \"system_user\" set password = #{password} where id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Select("select * from \"system_user\" where email = #{email}")
    AdminUserDO selectByEmail(String email);

    @Select("select * from \"system_user\" where id = #{id}")
    AdminUserDO selectById(Long id);

}
