/**
 * 抽屉「变量值」候选来源的行为用例（Node 内置 `node:test`，零新依赖）。
 *
 * 覆盖两条后端语义决定的规则：
 * 1. 迭代体内外不能互相引用（同层过滤）——容器折叠成 `blocks` 后，容器外节点的产出读不到，
 *    子块的产出也不会冒泡到父图，跨边界引用运行期一定取不到值；
 * 2. 迭代体子块能拿到容器「每轮注入」的变量（index / item / 迭代变量 / 当前项别名），
 *    这些变量不在任何节点的 `data.outputs` 里，必须由 `injectOutputsOf` 单独补出来。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import { expandIteration } from '../src/views/workflow2/utils/iteration.js'
import {
  injectOutputsOf,
  iterationOutputWarningsOf,
  unboundNamesOf,
  upstreamOutputsOf,
} from '../src/views/workflow2/utils/variable-sources.js'

/** 默认流程的画布表示：迭代节点按 `expandIteration` 展开成「容器 + 子块 + 内部边」 */
const canvas = () => {
  const nodes = []
  const edges = []
  for (const node of DEFAULT_FLOW_DEFINITION.nodes) {
    if (String(node.type) === '4') {
      const expanded = expandIteration(node)
      nodes.push(...expanded.nodes)
      edges.push(...expanded.edges)
      continue
    }
    nodes.push(node)
  }
  for (const edge of DEFAULT_FLOW_DEFINITION.edges) {
    edges.push({ id: edge.id, source: edge.sourceNodeID, target: edge.targetNodeID })
  }
  return { nodes, edges }
}

/** 默认流程中的关键 id：迭代容器 / 迭代体子块 / 迭代源 / 结束节点 */
const CONTAINER = '132996'
const CHILD = '173594'
const PLANNER = '100002'
const START = '100001'
const END = '900001'

test('迭代容器下游的顶层节点看不到迭代体子块的产出（跨迭代体引用）', () => {
  const { nodes, edges } = canvas()
  const outputs = upstreamOutputsOf(nodes, edges, END)

  assert.equal(
    outputs.some((item) => item.blockID === CHILD),
    false,
    '子块 173594 的产出只活在迭代体子图里，不该出现在父图节点的候选里',
  )
  assert.ok(
    outputs.some((item) => item.blockID === CONTAINER && item.name === 'result'),
    '容器自身的聚合产出对下游仍然可见',
  )
  assert.ok(outputs.some((item) => item.blockID === PLANNER), '同为顶层的上游节点仍然可见')
})

test('迭代体子块只看到同层兄弟，看不到容器外节点与容器的聚合产出', () => {
  const { nodes, edges } = canvas()
  const outputs = upstreamOutputsOf(nodes, edges, CHILD)

  assert.equal(
    outputs.some((item) => item.blockID === START || item.blockID === PLANNER),
    false,
    '迭代体子图里读不到容器外节点的产出',
  )
  assert.equal(
    outputs.some((item) => item.blockID === CONTAINER),
    false,
    '容器的聚合产出是迭代结束后才有的，迭代体内不可用',
  )
})

test('迭代体子块能拿到容器每轮注入的变量（index / item / 迭代变量）', () => {
  const { nodes } = canvas()
  const container = nodes.find((node) => node.id === CONTAINER)
  const inject = injectOutputsOf(nodes, container, CHILD)
  const names = inject.map((item) => item.name)

  assert.ok(names.includes('index'), '每轮注入轮次下标')
  assert.ok(names.includes('item'), '每轮注入当前项')
  assert.ok(names.includes('jj'), '容器 variableParameters 声明的中间变量')
  assert.equal(names.length, 3, '同名变量只出现一次（item 既在固定注入项里，也是子块声明的别名）')
  assert.equal(
    inject.every((item) => item.blockID === CONTAINER),
    true,
    '注入变量统一回填容器 id',
  )
})

test('顶层节点没有「每轮注入」变量', () => {
  const { nodes } = canvas()
  const container = nodes.find((node) => node.id === CONTAINER)
  assert.deepEqual(injectOutputsOf(nodes, container, END), [])
})

/* ===== 未绑定取值：卡片告警色与运行前校验共用的口径 ===== */

test('未绑定取值：只认 { blockID, name } 引用，常量（content）不算绑定', () => {
  const declared = [
    'plain', // 字符串写法 = 只有名字
    { name: 'named' }, // 对象写法、没有取值槽
    { name: 'bound', input: { blockID: '100001', name: 'file_tree' } },
    { name: 'half', input: { blockID: '100001' } }, // 有 blockID 没 name
    { name: 'constant', input: { content: '固定文案' } },
    { name: '' }, // 空名在卡片上不可见，忽略
  ]
  assert.deepEqual(
    [...unboundNamesOf(declared)],
    ['plain', 'named', 'half', 'constant'],
    '常量不算绑定：后端 NodeStateReader#read 只看 blockID / name，写了常量运行时同样取 null',
  )
  assert.deepEqual([...unboundNamesOf(undefined)], [], '缺省安全')
})

test('未绑定取值：默认流程的所有输入 / 迭代变量都有来源（卡片不该出现告警色）', () => {
  const { nodes } = canvas()
  const offenders = nodes.flatMap((node) => [
    ...[...unboundNamesOf(node.data?.inputs)].map((name) => `${node.id}:输入:${name}`),
    ...[...unboundNamesOf(node.data?.variableParameters)].map((name) => `${node.id}:迭代变量:${name}`),
  ])
  assert.deepEqual(offenders, [])
})

/** 卡片源码（SFC 无法在零依赖的 node:test 里挂载，接线用源码契约守住） */
const cardSourceOf = (file) =>
  readFileSync(new URL(`../src/views/workflow2/node/${file}`, import.meta.url), 'utf8')

test('卡片接线：输入 / 迭代变量都挂上未绑定告警，且口径唯一', () => {
  // 旧实现只有大模型卡片挂了这个 prop（VariableTags 的注释还写着「其余节点没有这个概念」），
  // 于是迭代节点的迭代源完全没绑定时卡片毫无提示、人工确认同理——这里把四张卡片一起锁住。
  // 只锁「绑的是哪个口径」，不锁行标签：标签属文案（结束节点这行的 label 曾从「输入」改成「输出」，
  // 一度把这条用例染红），而 `unboundInputNames` / `unboundParamNames` 两个名字本身已足以区分同一卡片内的多行。
  const cards = {
    'LlmNode.vue': ['unboundInputNames'],
    'IterateNode.vue': ['unboundInputNames', 'unboundParamNames'],
    'HumanNode.vue': ['unboundInputNames'],
    'EndNode.vue': ['unboundInputNames'],
  }

  for (const [file, names] of Object.entries(cards)) {
    const source = cardSourceOf(file)
    assert.match(
      source,
      /import \{ unboundNamesOf \} from '\.\.\/utils\/variable-sources'/,
      `${file} 必须复用统一口径（unboundNamesOf），不能自己写一份判定`,
    )
    for (const name of names) {
      assert.match(
        source,
        new RegExp(`<VariableTags[^>]*?:undefined-names="${name}"`),
        `${file} 要有一行 VariableTags 把告警名单接到 ${name} 上`,
      )
    }
  }
})

test('迭代输出与子块产出同名时不告警，不同名时告警', () => {
  const { nodes } = canvas()
  const container = nodes.find((node) => node.id === CONTAINER)
  const children = nodes.filter((node) => node.parentNode === CONTAINER)

  assert.equal(iterationOutputWarningsOf(container, children).size, 0, '默认流程里两边都是 result')

  const renamed = { ...container, data: { ...container.data, outputs: [{ name: 'summary' }] } }
  assert.deepEqual([...iterationOutputWarningsOf(renamed, children)], ['summary'])

  const childWithoutOutputs = children.map((child) => ({ ...child, data: { ...child.data, outputs: [] } }))
  assert.equal(
    iterationOutputWarningsOf(renamed, childWithoutOutputs).size,
    0,
    '子块还没声明产出时不报错（中间态）',
  )
})
