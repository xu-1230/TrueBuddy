package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.textflow.entity.NodeFieldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作流状态的取值工具：统一约定 {@code state[节点 id][变量名]}。
 */
public final class NodeStateReader {

    private NodeStateReader() {
    }

    /**
     * 按引用 {@code {blockID, name}} 从状态里取值：先取 {@code state[blockID]}，再取其中的 {@code name}。
     *
     * @return 取不到（节点未产出、产出不是 Map、字段不存在）时返回 null
     */
    @SuppressWarnings("unchecked")
    public static Object read(OverAllState state, NodeFieldRef ref) {
        if (state == null || ref == null || ref.getBlockID() == null) {
            return null;
        }
        Object nodeOutput = state.value(ref.getBlockID()).orElse(null);
        if (nodeOutput instanceof Map<?, ?> outputMap) {
            return ((Map<String, Object>) outputMap).get(ref.getName());
        }
        return null;
    }

    /**
     * 把取值归一化成列表：List 原样返回，数组转成 List，其余（含 null）返回空列表。
     * <p>用于迭代节点的迭代源：声明为 list 的输入可能来自数组或单个值。
     */
    public static List<Object> toList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value != null && value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            List<Object> result = new ArrayList<>(array.length);
            for (Object element : array) {
                result.add(element);
            }
            return result;
        }
        return new ArrayList<>();
    }
}
