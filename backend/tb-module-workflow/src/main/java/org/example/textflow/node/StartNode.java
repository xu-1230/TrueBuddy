package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.WorkflowNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开始节点（type = 1）：把本次运行的业务输入写进状态（{@code state[节点id][变量名]}）。
 *
 * <p><b>取值口径（docs/12-工作流规范.md §4.4）</b>：按 {@code data.outputs} 的声明逐个读取
 * 状态里的**顶层同名键** —— {@code POST /chat} 请求体的 {@code inputs} 经
 * {@code ChatServiceImpl#initialState} 并入初始状态。本节点不回查数据库、也没有演示兜底，
 * 数据由调用方显式提供：
 * <ul>
 *   <li>批改页：上传返回的文件清单（{@code file_tree}）、教师填写的批改要求（{@code description}）
 *       与所选知识库（{@code know}）随 inputs 直传；</li>
 *   <li>画布试运行：运行前在「输入数据」弹窗里按本节点声明显式填写。</li>
 * </ul>
 *
 * <p><b>required 校验</b>：声明为 required 的变量缺失（取不到、string 只有空白、list 为空列表）
 * 时直接抛 {@link IllegalStateException}，消息列出缺失变量与两条提供途径——异常沿
 * {@code failRun} 转成 {@code chat.failed} 帧，不再出现「流程成功但产出全是演示数据或空值」。
 * 非 required 变量（如 {@code know}）缺省时按类型给空值（list → 空列表、string → 空串），
 * 保证产出变量在状态里存在，下游取键不会拿到缺失键。
 */
public class StartNode implements NodeAction {

    private final WorkflowNode definition;

    public StartNode(WorkflowNode definition) {
        this.definition = definition;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        NodeData data = definition.getData();
        List<NodeField> declared = data == null || data.getOutputs() == null
                ? List.of()
                : data.getOutputs();

        Map<String, Object> outputs = new LinkedHashMap<>();
        for (NodeField field : declared) {
            String name = field.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            Object value = state == null ? null : state.value(name).orElse(null);
            if (isRequired(field) && isMissing(value)) {
                throw new IllegalStateException(missingMessage(field));
            }
            if (value == null) {
                value = emptyValueOf(field.getType());
            }
            outputs.put(name, value);
        }
        return Map.of(definition.getId(), outputs);
    }

    /** required 缺失的判定：取不到、文本只有空白、列表为空——三者都视为「没有提供数据」 */
    private static boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    /** 非 required 变量的类型缺省值：保持「产出变量在状态里存在」的口径 */
    private static Object emptyValueOf(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "list" -> List.of();
            case "string" -> "";
            default -> null;
        };
    }

    private static boolean isRequired(NodeField field) {
        return Boolean.TRUE.equals(field.getRequired());
    }

    /** 缺失提示：带变量说明与两条提供途径，避免使用者只看到「变量缺失」却不知道去哪填 */
    private static String missingMessage(NodeField field) {
        String label = field.getDescription() == null || field.getDescription().isBlank()
                ? field.getName()
                : field.getName() + "（" + field.getDescription() + "）";
        return "开始节点缺少必填输入变量「" + label + "」"
                + "：请在 POST /chat 的 inputs 中提供"
                + "（批改页发起执行时会自动携带；画布试运行请在运行前的「输入数据」弹窗中填写）";
    }

}
