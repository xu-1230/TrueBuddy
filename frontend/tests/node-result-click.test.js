/**
 * 「点击运行结果不应打开节点数据抽屉」的声明层回归用例（Node 内置 `node:test`，零新依赖）。
 *
 * 背景：结果面板渲染在节点组件模板里，属于 Vue Flow `.vue-flow__node` 的 DOM 子树；
 * 而节点根元素上绑着 `onClick → onSelectNode → emit.click → nodeClick`
 * （见 node_modules/@vue-flow/core/dist/vue-flow-core.mjs 的 `onClick: onSelectNode`），
 * 画布页的 `@node-click` 据此打开抽屉。因此这些「非节点本体」的容器必须在自身终止 click 冒泡。
 *
 * 这是**源码声明层**断言（不做 DOM 渲染），用来锁住三处容器的停止冒泡属性与 `nodrag`，
 * 防止后续被改回；端到端行为仍以浏览器实测为准。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

/** 读取组件源码（路径相对本测试文件） */
const readSource = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf8')

const PANEL = readSource('../src/views/workflow2/components/NodeResultPanel.vue')
const ITERATE = readSource('../src/views/workflow2/node/IterateNode.vue')
const HUMAN = readSource('../src/views/workflow2/node/HumanNode.vue')

test('结果面板根元素：终止 click 冒泡 + 禁止拖节点，且状态类绑定不变', () => {
  assert.match(
    PANEL,
    /class="node-result nodrag"[\s\S]{0,200}@click\.stop/,
    '面板根元素必须同时带 nodrag 与 @click.stop',
  )
  assert.match(
    PANEL,
    /:class="`node-result--\$\{status\}`"/,
    '状态类（node-result--done 等）的绑定不能被改动',
  )
})

test('结果面板内部的「展开 JSON」按钮仍保留自己的点击行为', () => {
  assert.match(
    PANEL,
    /class="node-result__row"[\s\S]{0,120}@click="toggleJson\(item\.name\)"/,
    '根元素拦冒泡不影响内部按钮：先执行自身 handler，再被根元素终止',
  )
})

test('迭代卡片的结果区容器（含进度行）终止 click 冒泡 + 禁止拖节点', () => {
  assert.match(
    ITERATE,
    /class="iterate-result nodrag" @click\.stop/,
    '结果区容器必须同时带 nodrag 与 @click.stop',
  )
  assert.match(
    ITERATE,
    /class="iterate-result nodrag" @click\.stop[\s\S]*?class="iterate-progress"/,
    '进度行在结果区容器内部，由容器统一拦截（点「n/N 轮」也不该开抽屉）',
  )
})

test('人工确认节点的等待确认区终止 click 冒泡 + 禁止拖节点', () => {
  assert.match(
    HUMAN,
    /class="node-confirm nodrag" @click\.stop/,
    '等待确认区必须同时带 nodrag 与 @click.stop（点「填写」不该开抽屉）',
  )
  assert.match(
    HUMAN,
    /class="node-confirm__btn nodrag"[\s\S]{0,80}@click="openCard"/,
    '「填写」按钮自己的 handler 保留（填写与提交都在画布页的弹窗里完成）',
  )
})

test('节点根元素不能拦截 click（否则点卡片就打不开抽屉了）', () => {
  for (const [name, source] of [['IterateNode', ITERATE], ['HumanNode', HUMAN]]) {
    assert.match(source, /class="node-wrapper"/, `${name} 仍应是 .node-wrapper 作为节点根`)
    assert.equal(
      /class="node-wrapper"[^>]*@click/.test(source),
      false,
      `${name} 的节点根不能挂 @click，否则点击卡片本体也打不开抽屉`,
    )
  }
})

test('直接用 <NodeResultPanel> 的节点无需额外容器（由面板根元素统一覆盖）', () => {
  for (const file of ['StartNode.vue', 'LlmNode.vue', 'EndNode.vue']) {
    const source = readSource(`../src/views/workflow2/node/${file}`)
    assert.match(source, /<NodeResultPanel v-if="runtime" :runtime="runtime" :node-id="id" \/>/,
      `${file} 直接渲染结果面板，没有中间包裹层`)
  }
})
