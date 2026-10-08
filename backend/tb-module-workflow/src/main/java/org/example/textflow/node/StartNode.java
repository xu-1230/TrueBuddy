package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
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

    /**
     * 模拟文件树：元素格式与 {@code POST /homework/grade} 的 {@code files}（{@code List<HomeworkFileItemVO>}）一致，
     * 样例取自 test2 的作业文件文本读取接口（{@code HomeworkFileTextController}）。
     * <p>带 fileId 才能让大模型直接调 {@code read_homework_file_text(fileId)} 读取文件内容。
     */
    private static final List<HomeworkFileItemVO> DEFAULT_FILE_TREE = List.of(
            fileItem(31L, 99L, "test/test1.txt", "text/plain"));

    /** 按批改请求 files 元素的格式构造一条模拟文件（name 取路径末段；size / url 留空，与样例一致） */
    private static HomeworkFileItemVO fileItem(Long id, Long fileId, String path, String type) {
        HomeworkFileItemVO item = new HomeworkFileItemVO();
        item.setId(id);
        item.setFileId(fileId);
        item.setName(path.substring(path.lastIndexOf('/') + 1));
        item.setPath(path);
        item.setType(type);
        return item;
    }

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
