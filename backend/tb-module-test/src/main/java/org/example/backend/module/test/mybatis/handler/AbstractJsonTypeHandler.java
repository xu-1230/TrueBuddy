package org.example.backend.module.test.mybatis.handler;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 文本与 Java 集合/映射类型互转的 TypeHandler 基类。
 * 用于 PostgreSQL JSONB 列与 Map / List 字段的自动转换。
 */
public abstract class AbstractJsonTypeHandler<T> extends BaseTypeHandler<T> {

    private final TypeReference<T> typeReference;

    protected AbstractJsonTypeHandler(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, JSONUtil.toJsonStr(parameter));
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private T parse(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return JSONUtil.toBean(json, typeReference, false);
    }
}