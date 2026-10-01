package org.example.textflow.node;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迭代源归一化（{@link NodeStateReader#toList(Object)}）的行为用例。
 *
 * <p>覆盖修复前的两处问题（见 {@code docs/12-工作流规范.md} §4.5 的迭代源口径）：
 * <ul>
 *   <li>原始类型数组（{@code int[]}）先判 {@code isArray()} 再强转 {@code Object[]}，会抛 {@link ClassCastException}；</li>
 *   <li>非列表的标量 / 对象被直接丢弃成空列表，迭代体一轮都不跑且没有任何告警。</li>
 * </ul>
 */
class NodeStateReaderTest {

    /** List 原样复制返回 */
    @Test
    void toList_keepsListAsIs() {
        assertThat(NodeStateReader.toList(List.of("甲", "乙"))).containsExactly("甲", "乙");
    }

    /** 对象数组转成 List */
    @Test
    void toList_convertsObjectArray() {
        assertThat(NodeStateReader.toList(new String[]{"甲", "乙"})).containsExactly("甲", "乙");
    }

    /** 原始类型数组按元素取出（不再抛 ClassCastException） */
    @Test
    void toList_readsPrimitiveArray() {
        assertThat(NodeStateReader.toList(new int[]{1, 2})).containsExactly(1, 2);
        assertThat(NodeStateReader.toList(new boolean[]{true})).containsExactly(true);
    }

    /** 取不到值（null）视为「迭代源还没产出」，返回空列表 */
    @Test
    void toList_nullBecomesEmptyList() {
        assertThat(NodeStateReader.toList(null)).isEmpty();
    }

    /** 非列表的标量 / 对象按单项处理：信息不再被静默丢弃 */
    @Test
    void toList_scalarBecomesSingleItemList() {
        assertThat(NodeStateReader.toList("电子技术基础-第3次作业")).containsExactly("电子技术基础-第3次作业");
        assertThat(NodeStateReader.toList(Map.of("name", "张三"))).containsExactly(Map.of("name", "张三"));
    }
}
