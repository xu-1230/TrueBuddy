package org.example.textflow.entity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流定义的读取入口：把 JSON 反序列化为 {@link WorkflowProcessRespVO}，并校验连线 id。
 */
public final class WorkflowProcessLoader {

    /** 默认定义文件（resources/text.json） */
    public static final String DEFAULT_LOCATION = "text.json";

    /** 顶层连线在校验信息里的位置描述 */
    private static final String TOP_LEVEL_EDGES = "顶层 edges";

    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private WorkflowProcessLoader() {
    }

    /**
     * 读取 classpath 下的工作流定义文件。
     *
     * @param location classpath 相对路径，如 "text.json"
     */
    public static WorkflowProcessRespVO loadFromClasspath(String location) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(location)) {
            if (in == null) {
                throw new IOException("classpath 下未找到工作流定义文件: " + location);
            }
            return validate(MAPPER.readValue(in, WorkflowProcessRespVO.class));
        }
    }

    /** 读取默认的 text.json。 */
    public static WorkflowProcessRespVO loadDefault() throws IOException {
        return loadFromClasspath(DEFAULT_LOCATION);
    }

    /** 解析 JSON 字符串。 */
    public static WorkflowProcessRespVO parse(String json) throws IOException {
        return validate(MAPPER.readValue(json, WorkflowProcessRespVO.class));
    }

    /**
     * 校验连线 id：每条边（顶层 edges 与节点内部的迭代体 edges）都必须声明非空 id，且全图唯一。
     * <p>id 只作标识、不参与流转计算，但定义数据依赖它唯一定位一条边，故缺失或重复时直接失败，避免运行期歧义。
     * <p>对文件读取与前端提交的定义统一生效，控制器可直接调用本方法校验请求体。
     *
     * @return 入参本身，便于在反序列化后直接串联调用
     * @throws IllegalArgumentException id 缺失或重复
     */
    public static WorkflowProcessRespVO validate(WorkflowProcessRespVO process) {
        if (process == null) {
            return null;
        }
        Map<String, String> seen = new HashMap<>();
        List<WorkflowEdge> edges = process.getEdges();
        if (edges != null) {
            for (WorkflowEdge edge : edges) {
                registerEdgeId(seen, edge == null ? null : edge.getId(), TOP_LEVEL_EDGES);
            }
        }
        List<WorkflowNode> nodes = process.getNodes();
        if (nodes == null) {
            return process;
        }
        for (WorkflowNode node : nodes) {
            if (node == null || node.getEdges() == null) {
                continue;
            }
            String where = "节点 " + node.getId() + " 的内部 edges";
            for (Map<String, Object> raw : node.getEdges()) {
                Object id = raw == null ? null : raw.get("id");
                registerEdgeId(seen, id == null ? null : String.valueOf(id), where);
            }
        }
        return process;
    }

    /** 登记一条边的 id：为空直接抛错，重复则带上两处位置信息。 */
    private static void registerEdgeId(Map<String, String> seen, String id, String where) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("连线 id 不能为空（" + where + "）");
        }
        String previous = seen.putIfAbsent(id, where);
        if (previous != null) {
            throw new IllegalArgumentException("连线 id 重复: " + id + "（" + previous + " 与 " + where + "）");
        }
    }

}
