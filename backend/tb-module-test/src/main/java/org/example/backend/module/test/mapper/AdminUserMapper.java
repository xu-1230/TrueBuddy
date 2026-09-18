package org.example.backend.module.test.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.test.model.AdminUserDO;

@Mapper
public interface AdminUserMapper {

    @Select("select * from \"system_user\" where username = #{username}")
    AdminUserDO selectByUsername(String username);
    
    @Insert("insert into \"system_user\" (username, password) values (#{username}, #{password})")
    int insert(AdminUserDO adminUser);

    @Select("select * from \"system_user\" where email = #{email}")
    AdminUserDO selectByEmail(String email);        

    @Select("select * from \"system_user\" where id = #{id}")   
    AdminUserDO selectById(Long id);        

}
