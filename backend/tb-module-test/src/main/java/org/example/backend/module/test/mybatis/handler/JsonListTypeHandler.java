package org.example.backend.module.test.mybatis.handler;

import cn.hutool.core.lang.TypeReference;

import java.util.List;

/**
 * List<String> 类型与 PostgreSQL JSONB 列的 TypeHandler。
 */
public class JsonListTypeHandler extends AbstractJsonTypeHandler<List<String>> {

    public JsonListTypeHandler() {
        super(new TypeReference<List<String>>() {
        });
    }
}