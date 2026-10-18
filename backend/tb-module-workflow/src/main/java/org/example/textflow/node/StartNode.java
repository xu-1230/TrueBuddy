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
 * 开始节点（type = 1）：把本次运行的业务输入写进状态（{@code state[节点id][变量名]}）。
 *
 * <p><b>输入优先取状态，缺失才回落下面的演示常量</b>：{@code POST /chat} 的 {@code inputs} 会被
 * 并入初始状态的顶层键（见 {@code ChatServiceImpl#initialState}），键名与本文的变量名一致，
 * 于是批改页能把「上传得到的文件清单 + 教师填的批改要求」直接喂进来。
 *
 * <p>仍**不读 {@code data.outputs}**：产出哪些变量由本文决定，与内置定义里开始节点声明的
 * {@code file_tree} / {@code description} / {@code know} 保持一致即可。
 *
 * <p>演示常量里的 fileId 是 {@code testfile/test} 三份教案经 {@code POST /homework/upload}
 * 上传后回查得到的真实 id（fileId 是模型调 {@code read_homework_file_text(fileId)} 读原文的唯一凭据）。
 */
public class StartNode implements NodeAction {

    private final WorkflowNode definition;

    /** docx 的 MIME；Tika 的文本提取按扩展名优先，type 只作展示 */
    private static final String DOCX_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * 模拟文件树：元素格式与 {@code POST /homework/grade} 的 {@code files}（{@code List<HomeworkFileItemVO>}）一致。
     * <p>带 fileId 才能让大模型直接调 {@code read_homework_file_text(fileId)} 读取文件内容。
     * <p>取自 {@code testfile/test}：3 名学生各 1 份教案；{@code test/2301130107 华技术/教案-数据结构.docx}
     * 原本是 {@code 教案-数据结构.zip}，因后端 {@code ALLOWED_EXTENSIONS} 不接受 zip，按「zip 内那份 docx
     * 才是该学生的提交」解包后再上传。
     */
    private static final List<HomeworkFileItemVO> DEFAULT_FILE_TREE = List.of(
            fileItem(47L, 122L, "test/2301130105 天才/教案-系统分析.docx", DOCX_TYPE),
            fileItem(48L, 123L, "test/2301130106 许小齐/教案-微服务.docx", DOCX_TYPE),
            fileItem(49L, 124L, "test/2301130107 华技术/教案-数据结构.docx", DOCX_TYPE));

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

    /**
     * 演示用的 AI 批改说明（对应定义里开始节点的 `description`）。
     *
     * <p>这份文本**同时承载满分、评分维度、扣分规则与评语要求** —— 因为批改页面上只有
     * 一个「AI 描述说明」输入框（见 `frontend/src/views/homework/components/ConfigPanel.vue`
     * 的占位符），工作流的开始节点也只声明 `file_tree` / `description` / `know` 三个变量；
     * 满分与及格线由规划节点从这段文本里解析进 `plan`。
     */
    private static final String DEFAULT_AI_DESCRIPTION =
            "根据学生编写教案的主题效果深度测试：以满分 100 分评分，60 分及格；"
                    + "重点考查教案主题与课程内容的契合度、内容深度、环节呈现效果与格式规范性；"
                    + "概念性错误整项扣分，缺少图例或案例每处扣 3 分；评语需给出具体修改建议。";

    private static final List<Integer> DEFAULT_AI_know = List.of(1,2,3,4);

    public StartNode(WorkflowNode definition) {
        this.definition = definition;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("file_tree", orDefault(state, "file_tree", DEFAULT_FILE_TREE));
        outputs.put("description", stringOrDefault(state, "description", DEFAULT_AI_DESCRIPTION));
        outputs.put("know", orDefault(state, "know", DEFAULT_AI_know));
        return Map.of(definition.getId(), outputs);
    }

    /**
     * 读调用方传进来的业务输入（{@code POST /chat} 的 {@code inputs} 会被并入顶层状态），缺失时回落演示常量。
     *
     * <p>在此之前本节点只能读 Java 常量，"换数据集必须改代码 + 重启"（见
     * docs/specs/2026-10-14-批改作业工作流定义替换.md §7 第 1 条）。现在批改页可以把上传得到的
     * 文件清单与教师填的批改要求直接传进来。
     */
    private static Object orDefault(OverAllState state, String name, Object fallback) {
        Object value = state == null ? null : state.value(name).orElse(null);
        return value == null ? fallback : value;
    }

    /** 文本类输入：取不到或只有空白时回落演示常量（空串会把提示词里那一段变成空白） */
    private static String stringOrDefault(OverAllState state, String name, String fallback) {
        Object value = orDefault(state, name, null);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

}
