/**
 * 「点击运行 → 运行前校验 → 拦截 or 建会话并提交执行」**全链路**用例（Node 内置 node:test，零新依赖）。
 *
 * 为什么是「抽取真实函数体 + 替身环境」：本项目测试零依赖（没有 jsdom / @vue/test-utils），
 * 无法在 node:test 里挂载 SFC。所以这里直接读 `WorkflowView.vue` 的源码，把 `handleRun` 与
 * `showRunValidationErrors` 的**真实实现**取出来执行，只把「环境」换成替身：
 *
 * - **真实**：`buildRunDefinition`（运行前校验读的那份定义，内含 `collapseIterations` 折叠）、
 *   `buildChatBody`（提交给 `/chat` 的请求体）、`runValidationErrors`（校验）、Vue 的 `h` /
 *   服务端渲染（校验失败的弹窗内容）；
 * - **替身**：`useVueFlow` 的 `getNodes` / `getEdges`（外面包一层 `.value`）、`ElMessage`、
 *   `ElMessageBox`、`createConversation`（建会话）、`execute`（SSE 提交入口）、
 *   `cleanOrphanChildren`（真实现会删节点，这里只记调用）。
 *
 * 抽取 / 求值失败会直接抛错，所以源码一旦改名或改结构，这些用例会先红，不会悄悄退化成空测试。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { register } from 'node:module'
import { h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import { expandIteration } from '../src/views/workflow2/utils/iteration.js'
import { runValidationErrors } from '../src/views/workflow2/utils/run-validation.js'

// `src/api/workflow.js` 内部用 `@/` 别名，先挂解析钩子再动态 import（见 support/alias-hooks.mjs）
register(new URL('./support/alias-hooks.mjs', import.meta.url))
const { buildChatBody, buildRunDefinition } = await import('../src/api/workflow.js')

const VIEW_PATH = new URL('../src/views/workflow2/WorkflowView.vue', import.meta.url)
const TOOLS_PATH = new URL('../src/views/workflow2/components/ToolsWrap.vue', import.meta.url)

/** 默认流程的画布表示（与画布页 toCanvasFlow 同口径：迭代节点展开成「容器 + 子块 + 内部边」） */
const canvasOfDefaultFlow = () => {
  // 深拷贝定义：`DEFAULT_FLOW_DEFINITION` 是模块级常量，浅引用会让某条用例的就地修改
  // （改 data / 改 inputs）污染同一进程里后续的用例
  const definition = structuredClone(DEFAULT_FLOW_DEFINITION)
  const nodes = []
  const edges = []
  for (const node of definition.nodes) {
    if (String(node.type) === '4') {
      const expanded = expandIteration(node)
      nodes.push(...expanded.nodes)
      edges.push(...expanded.edges)
      continue
    }
    nodes.push(node)
  }
  edges.push(...definition.edges.map((edge) => ({
    id: edge.id,
    source: edge.sourceNodeID,
    target: edge.targetNodeID,
  })))
  return { nodes, edges }
}

/** 默认流程对应的工作流 id（执行按 workflow_id 读库，用例给一个固定值即可） */
const WORKFLOW_ID = '1000000000000000001'

/** 模拟真实删除：`handleDeleteKey` → `removeNodes([node], true, true)`（连带删掉它的连线） */
const deleteNode = ({ nodes, edges }, id) => ({
  nodes: nodes.filter((node) => node?.id !== id),
  edges: edges.filter((edge) => edge.source !== id && edge.target !== id),
})

/**
 * 从 SFC 源码里取出一个具名箭头函数的整段实现（按大括号配对，末尾即函数体结束）。
 *
 * 这些函数体里的字符串 / 注释都不含未配对的 `{`，因此括号计数足够；取不到直接抛错。
 *
 * @returns {{ name: string, source: string }}
 */
const extractFunction = (source, declaration) => {
  const start = source.indexOf(declaration)
  assert.ok(start > 0, `WorkflowView.vue 里找不到「${declaration}」，请同步更新用例`)
  let depth = 0
  let end = source.indexOf('{', start)
  for (let index = end; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        end = index
        break
      }
    }
  }
  return {
    name: declaration.match(/const ([A-Za-z0-9_$]+) =/)[1],
    source: source.slice(start, end + 1),
  }
}

/**
 * 把抽取出来的真实实现求值成可调用的函数：依赖全部由入参注入（替身），
 * `import.meta.env.DEV` 在 `new Function` 里不可用，替换成 `true`（保留开发期诊断日志）。
 */
const evaluateFunction = (source, declaration, deps) => {
  const { name, source: body } = extractFunction(source, declaration)
  const code = body.replaceAll('import.meta.env.DEV', 'true')
  // 这里要跑的就是源码里的真实函数体（SFC 无法在零依赖的 node:test 里挂载）
  const factory = new Function(...Object.keys(deps), `${code}\nreturn ${name}`)
  return factory(...Object.values(deps))
}

/**
 * 把 `handleRun` 装到一个可观测的替身环境里。
 *
 * `handleRun` 现在是 **async**（执行前要先建会话），因此调用方必须 `await`。
 *
 * @param {{nodes: Array, edges: Array}} store 画布 store 替身
 * @param {object} [options] 选项
 * @param {boolean} [options.streaming] 是否正在执行中
 * @param {string} [options.workflowId] 当前工作流 id；传 `''` 模拟「未保存的画布」
 */
const loadHandleRun = (store, options = {}) => {
  const calls = { execute: [], popup: [], info: [], warning: [], error: [], debug: [], conversation: [], cleanOrphan: 0 }
  // 浏览器里的 console 方法齐全，替身必须同款：源码里任何一处 console.log / warn / error 都不该把
  // 链路用例打成 `console.log is not a function`（`debug` 单独收进 calls.debug，供断言取用）
  const consoleCalls = { log: [], info: [], warn: [], error: [] }
  const handleRun = evaluateFunction(readFileSync(VIEW_PATH, 'utf8'), 'const handleRun = async () => {', {
    runState: { value: { streaming: Boolean(options.streaming) } },
    currentWorkflowId: { value: options.workflowId === undefined ? WORKFLOW_ID : options.workflowId },
    ElMessage: {
      info: (message) => calls.info.push(message),
      warning: (message) => calls.warning.push(message),
      error: (message) => calls.error.push(message),
    },
    cleanOrphanChildren: () => { calls.cleanOrphan += 1 },
    buildRunDefinition,
    getNodes: { value: store.nodes },
    getEdges: { value: store.edges },
    runValidationErrors,
    showRunValidationErrors: (errors) => calls.popup.push(errors),
    createConversation: async (workflowId) => {
      calls.conversation.push(workflowId)
      return { conversationId: 'conversation-1' }
    },
    buildChatBody,
    execute: (payload) => calls.execute.push(payload),
    console: {
      debug: (...args) => calls.debug.push(args),
      log: (...args) => consoleCalls.log.push(args),
      info: (...args) => consoleCalls.info.push(args),
      warn: (...args) => consoleCalls.warn.push(args),
      error: (...args) => consoleCalls.error.push(args),
    },
  })
  // 挂回同一个对象（不能浅拷贝：`cleanOrphan` 这类计数器是靠闭包原地自增的）
  calls.console = consoleCalls
  return { handleRun, calls }
}

/* ===== 链路：默认（合法）画布 ===== */

test('链路：合法画布点运行 → 校验通过（不弹窗）→ 建会话并提交执行', async () => {
  const store = canvasOfDefaultFlow()
  const { handleRun, calls } = loadHandleRun(store)

  await handleRun()

  assert.deepEqual(calls.popup, [], '校验通过时不该弹窗')
  assert.equal(calls.cleanOrphan, 1, '提交前会先做一次悬空子块清理')
  assert.deepEqual(calls.conversation, [WORKFLOW_ID], '执行前先按 workflow_id 建会话')
  assert.equal(calls.execute.length, 1, '校验通过后要提交执行')
  assert.equal(calls.execute[0].body.conversationId, 'conversation-1', '提交的是刚建出来的会话')
  assert.equal(calls.execute[0].body.workflowId, WORKFLOW_ID)
  assert.equal(calls.execute[0].body.message, undefined, '首轮不带 message（业务输入由开始节点提供模拟数据）')
  assert.deepEqual(calls.debug, [['[workflow] 运行前校验', '通过']], '开发期能看到校验跑过且通过')

  // 折叠口径仍然成立（保存与运行前校验都走 buildRunDefinition）：顶层节点（开始 / 大模型 / 容器 / 结束）
  // + 容器内折叠的子块。`/chat` 只提交 workflow_id，故这里直接验证折叠产物本身。
  const definition = buildRunDefinition(store.nodes, store.edges)
  assert.equal(definition.nodes.length, 4)
  assert.equal(definition.nodes[0].id, '100001')
  const container = definition.nodes.find((node) => node.id === '132996')
  assert.equal(container.blocks.length, 1, '迭代体子块被折叠进容器的 blocks')
  assert.equal(container.edges.length, 2, '迭代体内部连线被折叠进容器的 edges')
})

/* ===== 链路：未保存的画布（执行按 workflow_id 读库，没有 id 就不能跑） ===== */

test('链路：未保存的画布点运行 → 提示先保存，不建会话、不清理、不执行', async () => {
  const { handleRun, calls } = loadHandleRun(canvasOfDefaultFlow(), { workflowId: '' })

  await handleRun()

  assert.deepEqual(calls.warning, ['请先保存工作流再运行（执行按 workflow_id 读取已落库的定义）'])
  assert.equal(calls.cleanOrphan, 0, '拦在最前面，连悬空子块清理都不做')
  assert.equal(calls.conversation.length + calls.execute.length, 0)
})

/* ===== 链路：缺开始 / 缺结束（用户手删之后的真实 store 状态） ===== */

test('链路：删掉「结束」节点后点运行 → 弹窗列出原因，且**不提交执行**', async () => {
  const store = deleteNode(canvasOfDefaultFlow(), '900001')
  const { handleRun, calls } = loadHandleRun(store)

  await handleRun()

  assert.deepEqual(calls.popup, [['工作流缺少顶层的「结束」节点']], '校验失败要弹窗，并给出缺失的节点')
  assert.equal(calls.execute.length, 0, '校验失败必须拦住本次运行')
  assert.equal(calls.conversation.length, 0, '校验没过就不该建会话')
  assert.deepEqual(calls.debug[0], ['[workflow] 运行前校验', ['工作流缺少顶层的「结束」节点']])
})

test('链路：删掉「开始」节点后点运行 → 同样被拦住', async () => {
  const store = deleteNode(canvasOfDefaultFlow(), '100001')
  const { handleRun, calls } = loadHandleRun(store)

  await handleRun()

  // 期望值取真实校验器的输出：删掉「开始」后，原本引用它的变量会**连带**报出「变量引用有问题」，
  // 这条链路要保证的是「弹窗 = 校验器结果」，各条错误本身的内容由 tests/run-validation.test.js 负责
  const expected = runValidationErrors(store.nodes, store.edges)
  assert.equal(expected[0], '工作流缺少顶层的「开始」节点', '首要原因仍是缺开始')
  assert.deepEqual(calls.popup, [expected])
  assert.equal(calls.execute.length, 0)
})

/* ===== 链路：断链（顶层有节点没有连入的连线） ===== */

test('链路：删掉「迭代 → 结束」的连线后点运行 → 报断链并拦住（用户实际遇到的场景）', async () => {
  const store = canvasOfDefaultFlow()
  // 画布上「结束」没有连入的连线：后端会给它自动接 START 变成并行起点，运行后才表现为「失败 / 本次无产出变量」
  store.edges = store.edges.filter((edge) => edge.id !== 'e-132996-900001')
  const { handleRun, calls } = loadHandleRun(store)

  await handleRun()

  // 期望值取真实校验器的输出：这条线一断，「结束」声明的取值（引用迭代容器的 result）也同时失去来源
  const expected = runValidationErrors(store.nodes, store.edges)
  assert.equal(
    expected[0],
    '工作流存在断链：「结束」没有连入的连线，运行时会作为独立起点执行、拿不到上游数据',
    '断链必须在运行前被拦下，并指出是哪个节点',
  )
  assert.ok(expected.length > 1, '连带的「变量引用有问题」也一并列出')
  assert.deepEqual(calls.popup, [expected])
  assert.equal(calls.execute.length, 0, '断链时不能提交执行')
})

/* ===== 链路：变量没绑定取值（抽屉里只声明了名字、没选来源） ===== */

test('链路：输入变量没选取值来源后点运行 → 报未绑定并拦住', async () => {
  const store = canvasOfDefaultFlow()
  // 把「学生名单规划」的输入改成「只声明、没选来源」：后端遇到 input 为空会跳过该变量，
  // 提示词占位符替换成空串，运行照样「成功」——必须在运行前拦住。
  // 注意替换整条节点（而不是就地改 `data`）：夹具的顶层节点与 `DEFAULT_FLOW_DEFINITION` 共享引用
  store.nodes = store.nodes.map((node) => (
    node.id === '100002' ? { ...node, data: { ...node.data, inputs: [{ name: 'description' }] } } : node
  ))
  const { handleRun, calls } = loadHandleRun(store)

  await handleRun()

  assert.deepEqual(calls.popup, [[
    '变量还没有绑定取值：节点「学生名单规划」的输入变量「description」',
  ]])
  assert.equal(calls.execute.length, 0, '未绑定时不能提交执行')
})

/* ===== 链路：画布上连出回边（环） ===== */

test('链路：顶层连出回边后点运行 → 报循环连接并拦住', async () => {
  const store = canvasOfDefaultFlow()
  store.edges = [...store.edges, { id: 'e-back', source: '100002', target: '100001' }]
  const { handleRun, calls } = loadHandleRun(store)

  await handleRun()

  assert.deepEqual(calls.popup, [['工作流存在循环连接：节点首尾相连形成回路']])
  assert.equal(calls.execute.length, 0)
})

/* ===== 链路：其它分支 ===== */

test('链路：空画布点运行 → 先提示「还没有节点」，不弹校验窗也不执行', async () => {
  const { handleRun, calls } = loadHandleRun({ nodes: [], edges: [] })

  await handleRun()

  assert.deepEqual(calls.warning, ['画布上还没有节点，先添加节点再运行'])
  assert.deepEqual(calls.popup, [], '空画布走的是更早的分支，不进校验')
  assert.equal(calls.execute.length, 0)
})

test('链路：正在执行中点运行 → 只提示，不清理、不校验、不执行', async () => {
  const { handleRun, calls } = loadHandleRun(canvasOfDefaultFlow(), { streaming: true })

  await handleRun()

  assert.deepEqual(calls.info, ['正在执行中，请等待本次执行结束'])
  assert.equal(calls.cleanOrphan, 0)
  assert.equal(calls.popup.length + calls.execute.length, 0)
})

test('链路：建会话失败 → 提示原因，不提交执行', async () => {
  const store = canvasOfDefaultFlow()
  const calls = { execute: [], error: [] }
  const handleRun = evaluateFunction(readFileSync(VIEW_PATH, 'utf8'), 'const handleRun = async () => {', {
    runState: { value: { streaming: false } },
    currentWorkflowId: { value: WORKFLOW_ID },
    ElMessage: { info: () => {}, warning: () => {}, error: (message) => calls.error.push(message) },
    cleanOrphanChildren: () => {},
    buildRunDefinition,
    getNodes: { value: store.nodes },
    getEdges: { value: store.edges },
    runValidationErrors,
    showRunValidationErrors: () => {},
    createConversation: async () => { throw new Error('工作流不存在: 1') },
    buildChatBody,
    execute: (payload) => calls.execute.push(payload),
    console,
  })

  await handleRun()

  assert.deepEqual(calls.error, ['工作流不存在: 1'], '建会话失败要把后端原因展示出来')
  assert.equal(calls.execute.length, 0, '没有会话就不该提交执行')
})

/* ===== 校验失败弹窗的**内容**（VNode → HTML，确认错误真的会显示出来） ===== */

test('链路：校验失败的弹窗逐条列出错误，且用户填写的节点名被转义', () => {
  const captured = []
  const showRunValidationErrors = evaluateFunction(
    readFileSync(VIEW_PATH, 'utf8'),
    'const showRunValidationErrors = (errors) => {',
    {
      h,
      ElMessageBox: {
        alert: (message, title, options) => {
          captured.push({ message, title, options })
          return Promise.resolve()
        },
      },
    },
  )

  showRunValidationErrors(['工作流缺少顶层的「结束」节点', '节点「<img src=x onerror=alert(1)>」存在循环连接'])

  assert.equal(captured.length, 1, '只弹一次窗')
  assert.equal(captured[0].title, '运行前校验未通过')
  assert.equal(captured[0].options.type, 'warning')

  return renderToString(captured[0].message).then((html) => {
    assert.ok(html.includes('工作流缺少顶层的「结束」节点'), '错误要真的渲染出来（不是空弹窗）')
    assert.equal((html.match(/<div>/g) ?? []).length, 3, '外层容器 + 每条错误一行')
    assert.ok(html.includes('&lt;img src=x onerror=alert(1)&gt;'), '用户填写的节点名必须被转义，不能当 HTML 渲染')
  })
})

/* ===== 点击链路源码契约（按钮 → 事件 → handleRun，只有这一条路径） ===== */

test('链路：运行按钮只有一条路径（ToolsWrap 点击 → run 事件 → handleRun）', () => {
  const tools = readFileSync(TOOLS_PATH, 'utf8')
  const view = readFileSync(VIEW_PATH, 'utf8')

  assert.match(tools, /@click="\$emit\('run'\)"/, '按钮点击必须派发 run')
  assert.match(tools, /:disabled="running"/, '执行中禁用按钮')
  // 模板里属性是跨行写的，断言不能要求单行形态
  assert.match(
    view,
    /<ToolsWrap[\s\S]{0,240}?:running="runState\.streaming"[\s\S]{0,120}?@run="handleRun"/,
    '画布页必须把 run 接到 handleRun',
  )
  assert.equal(
    (view.match(/@run="/g) ?? []).length,
    1,
    '运行入口只有一处，不存在绕过校验的第二条链路',
  )
})
