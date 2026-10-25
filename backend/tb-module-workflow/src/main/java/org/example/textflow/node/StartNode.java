package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.backend.module.homework.model.HomeworkTaskDO;
import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
import org.example.backend.module.homework.service.HomeworkService;
import org.example.textflow.entity.WorkflowNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开始节点（type = 1）：把本次运行的业务输入写进状态（{@code state[节点id][变量名]}）。
 *
 * <p><b>取值优先级：批改任务 &gt; 状态里的显式变量 &gt; 演示常量</b>：
 * <ol>
 *   <li>状态里有 {@code taskId}（批改页在 {@code POST /chat} 的 {@code inputs} 里带上，
 *       见 {@code HomeworkGradingView} 的 {@code handleGrade}）时，按它回查 {@code tb-module-homework}：
 *       {@code file_tree} 取 {@code homework_file}、{@code description} / {@code know} 取
 *       {@code POST /homework/grade} 写进 {@code homework_task} 的那一份 —— 这是批改页的唯一常态路径；</li>
 *   <li>没有 {@code taskId} 时（画布页试运行、冒烟脚本）用 {@code inputs} 直接并入顶层状态的同名变量；</li>
 *   <li>两者都缺时才落到下面的演示常量。</li>
 * </ol>
 *
 * <p>仍**不读 {@code data.outputs}**：产出哪些变量由本文决定，与内置定义里开始节点声明的
 * {@code file_tree} / {@code description} / {@code know} 保持一致即可。
 *
 * <p>演示常量里的 fileId 是 {@code testfile/test} 三份教案经 {@code POST /homework/upload}
 * 上传后回查得到的真实 id（fileId 是模型调 {@code read_homework_file_text(fileId)} 读原文的唯一凭据）。
 */
public class StartNode implements NodeAction {

    private final WorkflowNode definition;

    /** 作业模块：按 taskId 取本次批改的真实输入；为 null 时（单测）只用状态里的显式变量 */
    private final HomeworkService homeworkService;

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

    public StartNode(WorkflowNode definition) {
        this(definition, null);
    }

    public StartNode(WorkflowNode definition, HomeworkService homeworkService) {
        this.definition = definition;
        this.homeworkService = homeworkService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> outputs = new HashMap<>();
        Long taskId = taskIdOf(state);
        if (taskId != null && homeworkService != null) {
            HomeworkTaskDO task = homeworkService.getTask(taskId);
            if (task == null) {
                throw new IllegalStateException("批改任务不存在: " + taskId);
            }
            outputs.put("file_tree", homeworkService.listFileItems(taskId));
            outputs.put("description", task.getDescription() == null ? "" : task.getDescription());
            outputs.put("know", spaceIdsOf(task.getSpaceIds()));
            return Map.of(definition.getId(), outputs);
        }
        outputs.put("file_tree", orDefault(state, "file_tree", DEFAULT_FILE_TREE));
        outputs.put("description", stringOrDefault(state, "description", DEFAULT_AI_DESCRIPTION));
        outputs.put("know", orDefault(state, "know", List.of()));
        return Map.of(definition.getId(), outputs);
    }

    /** 批改任务 id：由批改页在 {@code POST /chat} 的 {@code inputs} 里带进来，键名与上传返回一致 */
    private static Long taskIdOf(OverAllState state) {
        Object value = state == null ? null : state.value("taskId").orElse(null);
        return value == null ? null : Long.valueOf(String.valueOf(value).trim());
    }

    /** {@code homework_task.space_ids} 以逗号分隔存储；未选知识库时为空列表 */
    private static List<String> spaceIdsOf(String spaceIds) {
        if (spaceIds == null || spaceIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(spaceIds.split(",")).map(String::trim).filter(id -> !id.isEmpty()).toList();
    }

    /**
     * 读调用方传进来的业务输入（{@code POST /chat} 的 {@code inputs} 会被并入顶层状态），缺失时回落演示常量。
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
