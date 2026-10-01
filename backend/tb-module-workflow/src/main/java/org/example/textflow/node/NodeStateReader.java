package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.textflow.entity.NodeFieldRef;

import java.lang.reflect.Array;
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
     * 把取值归一化成列表，用于迭代节点的迭代源（声明为 list 的输入可能来自 List、数组或单个值）：
     * <ul>
     *   <li>{@link List} 原样复制返回；</li>
     *   <li>数组按元素逐个取出（用 {@link Array} 反射取值，**原始类型数组 int[] 也能正确处理**，
     *       不会被 {@code (Object[]) } 强转抛 {@link ClassCastException}）；</li>
     *   <li>{@code null} 返回空列表（迭代源还没产出）；</li>
     *   <li>其余标量 / 对象按**单项列表**处理（旧实现直接丢弃成空列表，会让迭代体一轮都不跑且没有任何告警）。</li>
     * </ul>
     */
    public static List<Object> toList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(Array.get(value, index));
            }
            return result;
        }
        if (value == null) {
            return new ArrayList<>();
        }
        List<Object> single = new ArrayList<>(1);
        single.add(value);
        return single;
    }
}
