package org.example.backend.module.test.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;
import org.example.backend.module.test.mybatis.handler.JsonListTypeHandler;
import org.example.backend.module.test.mybatis.handler.JsonMapTypeHandler;

@Mapper
public interface OAuth2AccessTokenMapper {

    /** user_type 列已废弃（保留 DEFAULT 0），不再读写 */
    @Insert("insert into \"system_oauth2_access_token\" " +
            "(access_token, refresh_token, user_id, user_info, scopes, expires_time) " +
            "values (#{accessToken}, #{refreshToken}, #{userId}, " +
            "#{userInfo,typeHandler=org.example.backend.module.test.mybatis.handler.JsonMapTypeHandler}::jsonb, " +
            "#{scopes,typeHandler=org.example.backend.module.test.mybatis.handler.JsonListTypeHandler}::jsonb, #{expiresTime})")
    int insert(OAuth2AccessTokenDO accessTokenDO);

    @Results(id = "accessTokenResultMap", value = {
            @Result(column = "user_info", property = "userInfo", typeHandler = JsonMapTypeHandler.class),
            @Result(column = "scopes", property = "scopes", typeHandler = JsonListTypeHandler.class)
    })
    @Select("select id, access_token, refresh_token, user_id, user_info, scopes, expires_time " +
            "from \"system_oauth2_access_token\" where access_token = #{accessToken}")
    OAuth2AccessTokenDO selectByAccessToken(String accessToken);
}