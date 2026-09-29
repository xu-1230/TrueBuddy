package org.example.backend.module.workflow.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟知识库检索（MVP 阶段不依赖 knowledgebase 模块）。
 *
 * <p>返回内容由「知识库名称 + 查询关键词」确定性派生，便于联调时判断检索链路是否被调用。
 */
@Slf4j
@Service
public class MockKnowledgeBaseService {

    /** 模拟知识库片段池 */
    private static final List<String> SNIPPET_POOL = List.of(
            "求解静态工作点须先画出直流通路，忽略耦合电容与旁路电容，再列输入回路与输出回路方程。",
            "小信号等效电路法要求先求静态工作点，再用 rbe = rbb' + (1+β)·26mV/IEQ 估算输入电阻。",
            "交流通路分析时直流电源置零（接地），耦合电容与旁路电容视为短路。",
            "共射放大电路电压放大倍数带负号，表示输出与输入反相，计算时须保留相位的说明。",
            "非线性失真主要由静态工作点偏低（截止失真）或偏高（饱和失真）引起，应结合波形图判断。");

    /**
     * 模拟检索。
     *
     * @param query         查询关键词（当前作业路径 / 批改要求）
     * @param knowledgeList 知识库列表（可为空）
     * @param topK          返回片段数
     */
    public List<String> retrieve(String query, List<String> knowledgeList, int topK) {
        int limit = Math.max(1, topK);
        List<String> snippets = new ArrayList<>(limit);
        String source = knowledgeList == null || knowledgeList.isEmpty()
                ? "默认知识库" : String.join("、", knowledgeList);
        int offset = Math.floorMod(query == null ? 0 : query.hashCode(), SNIPPET_POOL.size());
        for (int i = 0; i < limit; i++) {
            String snippet = SNIPPET_POOL.get((offset + i) % SNIPPET_POOL.size());
            snippets.add("【知识库片段】来源=" + source + "：" + snippet);
        }
        log.debug("[retrieve][模拟知识库检索完成] knowledgeList={}, topK={}, hits={}", knowledgeList, limit, snippets.size());
        return snippets;
    }

}
