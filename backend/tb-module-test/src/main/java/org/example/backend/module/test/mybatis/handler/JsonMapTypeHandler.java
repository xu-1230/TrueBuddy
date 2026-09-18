package org.example.backend.module.test.mybatis.handler;

import cn.hutool.core.lang.TypeReference;

import java.util.Map;

/**
 * Map<String, String> 类型与 PostgreSQL JSONB 列的 TypeHandler。
 */
public class JsonMapTypeHandler extends AbstractJsonTypeHandler<Map<String, String>> {

    public JsonMapTypeHandler() {
        super(new TypeReference<Map<String, String>>() {
        });
    }
}