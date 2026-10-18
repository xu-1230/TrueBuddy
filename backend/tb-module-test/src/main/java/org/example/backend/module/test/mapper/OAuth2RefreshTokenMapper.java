package org.example.backend.module.test.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.test.model.OAuth2RefreshTokenDO;
import org.example.backend.module.test.mybatis.handler.JsonListTypeHandler;

@Mapper
public interface OAuth2RefreshTokenMapper {

    /** user_type 列已废弃（保留 DEFAULT 0），不再读写 */
    @Insert("insert into system_oauth2_refresh_token " +
            "(refresh_token, user_id, scopes, expires_time) " +
            "values (#{refreshToken}, #{userId}, " +
            "#{scopes,typeHandler=org.example.backend.module.test.mybatis.handler.JsonListTypeHandler}::jsonb, #{expiresTime})")
    int insert(OAuth2RefreshTokenDO refreshTokenDO);

    @Results(id = "refreshTokenResultMap", value = {
            @Result(column = "scopes", property = "scopes", typeHandler = JsonListTypeHandler.class)
    })
    @Select("select id, refresh_token, user_id, scopes, expires_time " +
            "from system_oauth2_refresh_token where refresh_token = #{refreshToken}")
    OAuth2RefreshTokenDO selectByRefreshToken(String refreshToken);

    /**
     * 删除已消费的刷新令牌：返回 0 表示已被其它并发请求消费，调用方应拒绝本次刷新。
     */
    @Delete("delete from system_oauth2_refresh_token where id = #{id}")
    int deleteById(Long id);
}