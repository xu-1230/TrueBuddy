package org.example.backend.module.test.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.test.model.AdminUserDO;

@Mapper
public interface AdminUserMapper {

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
