package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.NodeFieldRef;
import org.example.textflow.entity.WorkflowNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开始节点（type = input）：接收用户原始输入。
 */
public class StartNode implements NodeAction {

    private final WorkflowNode definition;

    private static final List<String> DEFAULT_FILE_TREE = List.of(
            "电子技术基础-第3次作业/20230101-张三/第三章习题.docx",
            "电子技术基础-第3次作业/20230102-李四/第三章习题.docx",
            "电子技术基础-第3次作业/20230103-王五/-第三章习题.pdf",
            "电子技术基础-第3次作业/20230201-赵六/第三章习题.docx",
//            "电子技术基础-第3次作业/20230201-赵六/第三章习题/第三章习题.md",
            "电子技术基础-第3次作业/20230202-钱七/课堂笔记.md");

    /** 模拟 AI 批改说明 */
    private static final String DEFAULT_AI_DESCRIPTION =
            "你是一个教务作业批改辅助系统。现在给你一个文件树 file_tree，其中包含某次课程作业的提交文件路径。请你根据文件树，判断出教师最终需要批改和登记成绩的学生名单，并生成一份“成绩单结构化数据”。";
    private static final List<Integer> DEFAULT_AI_know = List.of(1,2,3,4);

    public StartNode(WorkflowNode definition) {
        this.definition = definition;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> objectMap1 = new HashMap<>();
        objectMap1.put("file_tree", DEFAULT_FILE_TREE);
        objectMap1.put("description", DEFAULT_AI_DESCRIPTION);
        objectMap1.put("know", DEFAULT_AI_know);
        return Map.of(definition.getId(),objectMap1);
    }

}
