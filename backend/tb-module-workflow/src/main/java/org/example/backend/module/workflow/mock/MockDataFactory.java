package org.example.backend.module.workflow.mock;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟输入数据工厂（MVP 阶段不接真实文件系统 / 知识库）。
 *
 * <p>请求入参未提供时会回退到这里的内置数据，保证接口开箱可跑。
 */
@Component
public class MockDataFactory {

    /** 模拟「文件夹文件树」：目录以 / 结尾，含文本类与非文本类文件 */
    private static final List<String> DEFAULT_FILE_TREE = List.of(
            "电子技术基础-第3次作业/20230101-张三/第三章习题.docx",
            "电子技术基础-第3次作业/20230102-李四/第三章习题.docx",
            "电子技术基础-第3次作业/20230103-王五/-第三章习题.pdf",
            "电子技术基础-第3次作业/20230201-赵六/第三章习题.docx",
            "电子技术基础-第3次作业/20230201-赵六/第三章习题/第三章习题.md",
            "电子技术基础-第3次作业/20230202-钱七/课堂笔记.md");

    /** 模拟 AI 批改说明 */
    private static final String DEFAULT_AI_DESCRIPTION =
            "本次批改《电子技术基础》第三章「放大电路」习题，重点检查静态工作点计算与交流通路分析过程，"
                    + "评分需体现公式推导的完整性，对仅有结果无过程的作业扣分。";

    /** 模拟知识库列表 */
    private static final List<String> DEFAULT_KNOWLEDGE_LIST =
            List.of("电子技术基础-课程标准", "第三章-放大电路-参考答案");

    /** 模拟满分 */
    private static final Integer DEFAULT_FULL_SCORE = 100;

    /** 默认文件树 */
    public List<String> fileTree() {
        return new ArrayList<>(DEFAULT_FILE_TREE);
    }

    /** 默认 AI 批改说明 */
    public String aiDescription() {
        return DEFAULT_AI_DESCRIPTION;
    }

    /** 默认知识库列表 */
    public List<String> knowledgeList() {
        return new ArrayList<>(DEFAULT_KNOWLEDGE_LIST);
    }

    /** 默认满分 */
    public Integer fullScore() {
        return DEFAULT_FULL_SCORE;
    }

    /**
     * 开始节点输出的兜底值：按变量名给出模拟数据，避免未传参时接口不可用。
     *
     * @param name 开始节点声明的输出变量名
     */
    public Object fallbackValue(String name) {
        if (name == null) {
            return null;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("fileTree", fileTree());
        fallback.put("knowledgeList", knowledgeList());
        fallback.put("aiDescription", aiDescription());
        fallback.put("fullScore", fullScore());
        return fallback.get(name);
    }

}
