# doc-workflow · 工作流业务逻辑

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-16
> 适用范围：后端工作流模块 `tb-module-workflow`（包名 `org.example.textflow`）的接口参数定义与工作流定义 JSON 规范
> 相关：[doc-api.md](./doc-api.md)（接口清单）、[doc-conventions.md](./doc-conventions.md)（项目结构与编码规范）、[doc.md](./doc.md)（硬约束）、[doc-homework.md](./doc-homework.md)（批改复用执行链）、[doc-database.md](./doc-database.md)（三张工作流表）、[adr/0006-知识库与工作流归属与共享模型.md](./adr/0006-知识库与工作流归属与共享模型.md)（归属与共享模型的决策依据）

---

## 1. 模块概览

| 项 | 值 |
| --- | --- |
| 模块坐标 | `tb-module-workflow`（目录 `backend/tb-module-workflow/`，已在 `backend/pom.xml` 注册） |
| Java 包名 | `org.example.textflow`（实现移植自独立工程 text-flow，故包名与模块名不一致） |
| 图引擎 | `com.alibaba.cloud.ai:spring-ai-alibaba-graph-core:2.0.0-M1.1`（版本在模块 `pom.xml` 显式声明，未纳入 `tb-dependencies` BOM） |
| 基础依赖 | `tb-spring-boot-starter-web`（传递引入 web / `tb-common` / hutool / lombok） |
| 持久化 | **已接库**：`repository/` 用 `spring-boot-starter-jdbc` 的 `JdbcTemplate` 手写 SQL，落 `workflow` / `workflow_conversation` / `workflow_run` 三张表（见 §6）。**不引入 MyBatis**，与其余业务模块的注解 SQL 风格不同，属该模块的既定取舍 |
| 定义来源 | 数字 id 读 `workflow` 表的 `schema_json`；非数字 id 回落 classpath 文件 |
| 默认定义文件 | `src/main/resources/text.json`（`WorkflowProcessLoader.DEFAULT_LOCATION`），另有 `text2.json` 与 `resources/workflow/` |

```text
backend/tb-module-workflow/
├── pom.xml
└── src/main/
    ├── java/org/example/textflow/
    │   ├── controller/   # ChatController：/chat（SSE 执行入口）、/chat/conversation、/chat/conversation/list
    │   │                 # WorkflowController：/workflow/save、/detail、/list、/text（联调）
    │   ├── entity/       # WorkflowProcessRespVO、WorkflowNode、WorkflowEdge、WorkflowPosition
    │   │                 # NodeData、NodeField、NodeFieldRef、WorkflowProcessLoader
    │   ├── model/        # ChatProtocol（事件协议常量）、WorkflowConstants
    │   │                 # WorkflowDO、WorkflowRunDO、WorkflowConversationDO、vo/*
    │   ├── node/         # NodeActionFactory、WorkflowGraphFactory（画布 → 可执行图）
    │   │                 # StartNode、EndNode、LlmNode、HumanNode、IterateNode
    │   │                 # NodeStateReader、NodeEventListener
    │   ├── repository/   # WorkflowDefinitionRepository、WorkflowRunRepository
    │   │                 # WorkflowConversationRepository（JdbcTemplate，单表读写）
    │   ├── service/      # WorkflowService、ChatService（/chat 的分流与执行）
    │   ├── sse/          # ChatSsePublisher（conversation.* 事件帧）
    │   └── init/         # WorkflowDefinitionInitializer（内置模板种子）
    └── resources/        # text.json（classpath 回落定义）、text2.json（当前未被引用）
```

---

## 2. 接口约定

### 2.1 接口清单

执行入口（`ChatController`，`@RequestMapping("/chat")`）与定义读写（`WorkflowController`，`@RequestMapping("/workflow")`）
分属两条前缀。后端 `context-path` 为 `/api`，故完整路径为 `/api/chat` 与 `/api/workflow/*`。
**执行没有挂在 `/workflow` 下是有意的**：`/workflow/**` 在 `tb.security.permit-all-urls` 里免登录，
而 `/chat` 要求登录（会话与执行实例都要记 `creator`）。

| # | 方法 | 路径 | 入参 | 返回 | 语义 |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | `/chat` | 请求体：`{conversationId, workflowId?, message?}` | `SseEmitter`（`text/event-stream`） | **执行入口**：首轮与中断续跑共用，由服务端分流；节点进度与卡片以 SSE 推送 |
| 2 | POST | `/chat/conversation` | 请求体：`{workflowId, title?}` | `CommonResult<ConversationVO>` | 新建会话（绑定**已落库**的工作流），返回 `conversationId` |
| 3 | GET | `/chat/conversation/list` | 无 | `CommonResult<List<ConversationVO>>` | 会话列表（按最近活跃倒序） |
| 4 | GET | `/workflow/text` | 无 | `void` | 同步 `invoke` classpath 定义一次并把终态打印到控制台，仅供联调，不是业务接口 |
| 5 | POST | `/workflow/save` | 请求体：`{id, name, description, schemaJson}`（`id` 空 = 新建、非空 = 更新） | `CommonResult<String>` | 保存工作流定义，返回**字符串**形式的雪花 id |
| 6 | GET | `/workflow/detail` | `@RequestParam("workflow_id") workflowId` | `CommonResult<WorkflowDetailRespVO>` | 工作流详情（含 `schemaJson` 画布字符串；`visibility` / `mine` / `builtin` 一并下发，画布页据此呈现只读形态，前端不猜归属） |
| 7 | GET | `/workflow/list` | 无 | `CommonResult<List<WorkflowVO>>` | 工作流列表（**不含** `schemaJson`，按更新时间倒序；**按 creator 过滤**——登录用户只见自己的 + builtin，匿名只见 builtin，见 §6.3） |
| 8 | PUT | `/workflow/visibility` | `@RequestParam("workflow_id") workflowId`、`@RequestParam("visibility") visibility`（0=私有/1=公有） | `CommonResult<Boolean>` | 切换可见性（**仅创建者**，builtin 拒绝；非法值 `code = 400`） |
| 9 | GET | `/workflow/public-list` | 无 | `CommonResult<List<PublicWorkflowVO>>` | 他人公有工作流列表（画布页「公有库」浏览区；**不含** `schemaJson`，带 `creatorName`；匿名可见全部公有行——`/workflow/**` 免登录白名单是 URL 级现状，见 §6.3） |
| 10 | POST | `/workflow/fork` | `@RequestParam("workflow_id") workflowId` | `CommonResult<String>` | fork 为当前用户的独立副本（他人公有流运行的唯一前置通道），返回副本 id |

> **已废弃**：`POST /workflow/run` 与 `POST /workflow/resume`（执行语义已合并进 `/chat`），
> 事件协议与载荷改造见 §2.2，人工确认节点的中断载荷见 §4.3。

强制约定：

- **执行入口只有一个 `/chat`**：首轮与续跑**共用同一个接口**，客户端**不传状态**——服务端取该会话最新一条
  `workflow_run` 自行分流（没有记录 / 已是终态 → 新起一条执行；`status = 4` → 续跑并复用同一条执行记录；
  `status ∈ {0,1}` → 拒绝，同一会话不允许并发执行）。客户端传状态既可伪造，也有「前端已收到中断帧、
  DB 落库还没提交」的时序竞态。
- **直跑闸（ADR 0006）**：建会话（`/chat/conversation`）与执行（`/chat`）**两个入口都校验** `own OR builtin`——
  只有创建者本人或 builtin 内置模板可绑定运行；他人的公有流一律 `code = 403`（「他人发布的工作流不能直接运行，
  请先 fork 为自己的副本」）。**公有 ≠ 可运行**，fork 是使用他人资源的唯一通道；builtin 豁免保证批改默认工作流可用。
- **`/chat` 只接受已落库的 `workflow_id`**：不接受内联画布定义，画布页点运行前必须先保存
  （未保存时前端拦住并提示）。定义来源：`WorkflowService#getProcess` 对数字 id 读 `workflow` 表，
  非数字 id（联调用的 `"text"`）回落 classpath `text.json`。
- **续跑提交结构化 JSON**：`message = {type: "card_submit", nodeId, values}`，`values` 的 key 对应中断节点
  `data.outputs` 声明的变量名；后端以 `addStateUpdate(Map.of("__human_feedback__", values))` 注入状态，
  人工确认节点的 `apply` 再按 `state[节点id][变量名]` 回写。**不学 Coze 把表单拼成「标签:值」文本**。
- **事件流响应不设超时**：`/chat` 使用 `new SseEmitter(0L)`。
- **`/chat` 要求登录**：它**不在** `tb.security.permit-all-urls` 白名单里（这也是它没有挂在 `/workflow`
  前缀下的原因——`/workflow/**` 是免登录的）。会话与执行实例都要记 `creator`。
- **校验失败返回统一返回体而非事件流**：会话不存在、工作流不存在、并发执行、续跑缺少中断点等一律抛
  `ServiceException`，由 `GlobalExceptionHandler` 转成 `CommonResult` JSON（`code = 400`）；
  客户端须按 `Content-Type` 判断收到的是事件流还是错误体。建图 / 执行期的异常发生在流已建立之后，
  只能以 `conversation.chat.failed` 帧回传（HTTP 仍 200）。
- **初始状态**：首轮的初始状态为 `{"__START__": "777"}`（占位值，无业务语义）；首轮**不接受业务输入**，
  开始节点的产出是模拟数据（`file_tree` / `description` / `know`）。续跑不传业务初始状态，只注入人工反馈。
- **落库范围**：只写 `workflow_run`（执行实例）与 `workflow_conversation`（会话）的**元数据**
  （状态、时间、耗时、标题）；卡片、最终答案与用户提交内容**只走事件流**，不落库。
  代价：**刷新 / 换设备后中断卡片不可回显；服务重启后该会话不可续跑**（中断点与检查点都在内存）。

### 2.2 SSE 事件约定

事件名与载荷由 `ChatSsePublisher` 构造，协议常量集中在 `ChatProtocol`。命名**对齐字节 Coze 开放平台的
`/v1/chat`**（`conversation.chat.*` / `conversation.message.*` / `done`），但**裁掉了冗余信号**：
同一件事只留一个帧——中断只用 `chat.requires_action`（不再叠 verbose 的 `msg_type=interrupt` 与
`finish_reason`），成败只用 `chat.completed` / `chat.failed`。

载荷字段用 **snake_case**（`conversation_id` / `execute_id` / `chat_id` …），与事件名同源、便于与 Coze 文档
逐字对照；REST 请求体仍是项目惯例的 camelCase。

| event | 触发时机 | data 关键字段 | 说明 |
| --- | --- | --- | --- |
| `conversation.chat.created` | 每轮第一帧 | `status:"created"`、`conversation_id`、`execute_id`、`chat_id`、`workflow_id`、`created_at`、`last_error` | 三个标识在此下发，见 §2.6 |
| `conversation.chat.in_progress` | 每轮第二帧 | 同上 + `status:"in_progress"` | 图开始执行 |
| `conversation.message.completed` | 每个节点完成 / 出卡片 / 出答案 | `role`、`type`、`content_type`、`content` | 三种载荷见下表 |
| `conversation.chat.requires_action` | 中断轮倒数第二帧 | `status:"requires_action"`、`completed_at` | **中断轮不发 `chat.completed`** |
| `conversation.chat.completed` | 成功轮倒数第二帧 | `status:"completed"`、`completed_at` | 执行成功 |
| `conversation.chat.failed` | 失败轮倒数第二帧 | `status:"failed"`、`last_error:{code,msg}` | 建图 / 执行期异常走这里（HTTP 仍 200） |
| `done` | 最后一帧 | `conversation_id`、`execute_id` | **每轮都发，与成败无关** |

`conversation.message.completed` 的三种载荷：

| `type` / `content_type` | content 结构 | 说明 |
| --- | --- | --- |
| `verbose` / `verbose` | `{msg_type:"node_finished", data:{node, state}}` | 节点产出。`data.node` 与 `data.state[node]` **与旧的 `node` 事件同形**（外层只是从顶层挪进消息帧），迭代体子块的事件同样走这里 |
| `answer` / `card` | `{response_type:"card", card_type:"INPUT", node_id, title, data:[…], fields:[…]}` | 人工确认中断的表单卡片，见 §4.3 |
| `answer` / `text` | 字符串 | 最终答案（结束节点组装正式结果后启用；当前 `EndNode` 仍是 TODO，故一期不发） |

`conversation.message.delta`（逐字增量）**保留事件名但不发送**：大模型节点当前是模拟产出、没有真流式，
前端按「有 delta 就逐字渲染、没有就整段渲染」实现，二期接真模型时不必改协议。

流结束方式：正常结束调用 `emitter.complete()`；**发送失败（客户端断开）只记 DEBUG 日志、不打断图执行**
——前端断开不改变服务端状态，执行照常跑完并把状态落到 `workflow_run`。

```text
event: conversation.chat.created
data: {"chat_id":"…","conversation_id":"…","execute_id":"…","workflow_id":"1000000000000000001","status":"created",…}

event: conversation.message.completed
data: {"chat_id":"…","conversation_id":"…","execute_id":"…","role":"assistant","type":"verbose","content_type":"verbose",
       "content":{"msg_type":"node_finished","data":{"node":"100001","state":{"100001":{"file_tree":[…],"description":"…","know":[1,2,3,4]}}}}}

event: conversation.message.completed
data: {…,"type":"answer","content_type":"card",
       "content":{"response_type":"card","card_type":"INPUT","node_id":"100003","title":"人工确认",
                  "data":[{"name":"plan","type":"list","label":"待确认清单","value":[…]}],
                  "fields":[{"name":"files","type":"list","required":true,"label":"作业文件","schema":{…}}]}}

event: conversation.chat.requires_action
data: {…,"status":"requires_action","completed_at":…}

event: done
data: {"conversation_id":"…","execute_id":"…"}
```

### 2.3 中断与恢复时序

**首轮（跑到人工确认节点中断）**

1. 调用 `POST /chat/conversation`（`{workflowId}`）拿到 `conversationId`。
2. 调用 `POST /chat`（`{conversationId, workflowId}`），依次收到：
   `conversation.chat.created` → `conversation.chat.in_progress` → 若干 `conversation.message.completed`（verbose 节点产出）
   → 一条 `content_type: card` 的 `conversation.message.completed`（弹窗数据）
   → `conversation.chat.requires_action` → `done`，**连接随即结束**。
3. 服务端把 `workflow_run.status` 落成 `4`（中断），并把中断点写入内存 `interruptions`（`executeId` → 元数据）。

**续跑（人工确认后继续）**

4. 用户在弹出的表单卡片里填写并提交，前端调用**同一个** `POST /chat`：
   `{conversationId, message: {type: "card_submit", nodeId, values}}`。
5. 服务端把 `status` 从 `4` 改回 `1`（**复用同一条执行记录，不新增行**），带上原中断对象与 `values`
   从检查点续跑；事件序列同上，成功时以 `conversation.chat.completed` 收尾（不再有卡片）。
6. 若该会话的**最新**执行不是中断（执行中 → 400 拒绝；终态 → 新起一条执行），或中断点已失效
   （服务重启），一律返回 `CommonResult`（`code = 400`）。

> 注意：**每轮都是新起一条流**（不是复用上一轮的连接）；中断点与 `MemorySaver` checkpoint 都只存内存，
> **进程重启即失效**，此时续跑会返回 400 提示重新发起执行。

### 2.4 三个标识的分工

| 标识 | 生成方 | 生命周期 | 用途 |
| --- | --- | --- | --- |
| `conversation_id` | `POST /chat/conversation` | 一个会话一个（跨轮不变） | 定位会话；`/chat` 必传 |
| `execute_id` | 首轮 `/chat` 创建执行记录时生成（UUID） | 一条执行一个；**续跑复用**（同一行 `workflow_run`，`VARCHAR(36)`，全局唯一索引） | 串联同一会话的多轮交互；轮询兜底按它命中 |
| `chat_id` | 每轮 `/chat` 生成（UUID） | 一轮一个（每轮新生成） | 前端区分「哪一轮」的消息 |

> Coze 的 `chat.created` 帧里 `execute_id` 与同轮 `chat.completed` / `done` 不一致（脏数据），
> **本实现不照抄**：同一条执行的 `execute_id` 全程一致。

### 2.5 前端展示与状态更新约定

画布页（`frontend/src/views/workflow2/WorkflowView.vue`）消费事件流后，把结果就地渲染到**对应节点的下方**，约定如下。

画布初始内容是 `workflow2/default-flow.js` 里的默认链路（开始 → 大模型 → 迭代（迭代体内一个大模型）→ 结束）：加载时用 `utils/iteration#expandIteration` 把迭代节点展开成「容器 + 子块 + 内部边」，提交执行前由 `buildRunDefinition` 折叠回 `blocks` / `edges`；迭代节点的输出变量名与迭代体子块的产出同名，以便后端按同名聚合。画布上的删除键（`Backspace` / `Delete`）由 `WorkflowView` 统一接管：迭代容器连同容器内的子块与相关连线一起删除（Vue Flow 的默认删除**只删节点本身**、不删子节点，容器被删后子块会变成悬空节点并被当作顶层节点提交执行），提交前还会清理 `parentNode` 悬空的残留节点。抽屉里的「变量值」候选按 `parentNode` 同层过滤：迭代体内外的变量不能互相引用（折叠成 `blocks` 后，容器外节点的产出只在父图状态里、子块的产出只在子图里，跨边界引用运行期一定取不到值）；迭代体内子块可选的「每轮注入」变量（`index` / `item` / `variableParameters` / 子块声明的当前项别名）由前端按 `IterateNode#buildRoundInput` 的口径补出。

节点状态取值（`views/workflow2/utils/run-state.js` 的 `RUN_STATUS`）：

| 状态 | 文案 | 进入时机 | 语义色 |
| --- | --- | --- | --- |
| `idle` | 待运行 | 本次执行没有走到该节点；流正常收尾时仍为「执行中」的节点也回落至此 | `--tb-text-tertiary`（灰） |
| `running` | 执行中 | 提交执行时，画布上的全部节点 | `--tb-primary`（主色） |
| `done` | 完成 | 收到该节点 id 的节点产出帧（verbose） | `--tb-success`（绿） |
| `waiting` | 等待确认 | 收到该节点的表单卡片帧（card），`node_id` 命中的节点 | `--tb-warning`（橙） |
| `failed` | 失败 | `chat.failed` 帧或流异常结束时仍未完成的节点 | `--tb-danger`（红） |

事件 → 状态与产出的更新逻辑：

| # | 触发 | 更新 |
| --- | --- | --- |
| 1 | 点击「运行」 | 先做运行前校验、再建会话（地址栏没有 `workflow_id` 时直接拦住并提示先保存）；提交前投影画布字段（节点 `id` / `type` / `position` / `data` / `blocks` / `edges`，边 `id` / `source` / `target`），剔除 Vue Flow 运行时字段；画布全部节点置 `running`，清空上一次产出 |
| 2 | `conversation.chat.created` | 记录 `conversation_id` / `execute_id` / `chat_id`（见 §2.4） |
| 3 | `conversation.message.completed`（`content_type=verbose`） | 该节点置 `done`，产出取 `content.data.state[content.data.node]` |
| 4 | `conversation.message.completed`（`content_type=card`） | 该节点置 `waiting`、`waitLabel` 取卡片 `title`，卡片数据一并存进运行态；画布页据此**自动弹出表单弹窗**（`HumanCardDialog`） |
| 5 | 用户点弹窗「确定」 | 请求 `POST /chat`（`message.values` 结构化回传），新起一条事件流，回到第 3 步继续更新 |
| 6 | `conversation.chat.completed` + 流关闭 | 仍为 `running` 的节点回落 `idle` |
| 7 | `conversation.chat.failed`，或 HTTP 失败 / 流断开 | 仍为 `running` 的节点回落 `failed`，并用 `ElMessage` 提示后端返回的 `message` |

人工确认的填写与提交都在**画布页的弹窗**里完成（不是节点卡片内嵌输入框）：节点卡片上只留一个
「填写」入口（节点根元素仍不挂 `@click`，点卡片本体照样打开节点抽屉）。续跑提交时运行态里的卡片会被
**消费掉**（`resumeRun` 清空卡片并把该节点拉回 `running`），避免弹窗关掉后还能再打开一张作废的表单。

运行结果的展示位置与内容：

- **位置**：所有节点（含迭代节点）的结果面板都贴在卡片正下方；迭代节点的结果区（进度行 + 面板）与卡片**同宽**，其余节点与卡片同为 17rem。
- **迭代节点结构**：节点就是一张**与普通节点完全同款的卡片**（`.node-card` 铺满节点盒：图标 + 名称 + 「迭代」标识 + 遍历对象 + 输入 / 迭代变量 / 输出三组变量行 + 右侧徽标，复用 `components/VariableTags.vue`），不同之处是卡片内部在变量行下方**嵌了一块「子节点画布」**（`.iterate-canvas`：左右与底部各内缩 `ITERATION_LAYOUT.canvasInset`，浅色底 + 边框 + 圆角），画布区内横向是「迭代开始」**图标胶囊**（锚定左侧：`HomeFilled`）→ 迭代体子节点 → 「迭代结束」图标胶囊（锚定右侧：`SwitchButton`），胶囊尺寸 28×28（`ITERATION_LAYOUT.markerWidth` / `markerHeight`，宽度贴合图标，因此迭代节点宽度为 448），**边框与底色与其它节点卡片一致**（实线 `--tb-border` + 实底 `--tb-bg-surface`）；两个标记**固定在画布区的垂直中点**（CSS 锚定，不随子块拖动移动，悬停有 `title` 提示），子块区初始也在画布区内垂直居中（`childrenTopOf(子块数)` 上下留白均分），因此初始状态下三者同一水平线、内部连线水平进出子块。运行状态与汇总产出不在画布区内，展示在卡片正下方（进度行 + `components/NodeResultPanel.vue` 结果面板，面板与卡片同宽）。
- **尺寸可拖拽改变**：卡片右下角有拖拽把手（`nodrag nopan`），按住拖动即改变节点尺寸——屏幕位移按当前缩放（`viewport.zoom`）换算成画布位移，经 `updateNode` 写回节点 `style`，下限为 `minNodeSizeOf(顶部内容区高度, 子块数)`（宽度放得下「开始标记 → 子块 → 结束标记」的横向排列，高度放得下顶部内容区 + 画布区**且装得下当前子块**——与初始尺寸 `containerSizeOf(子块数, 顶部内容区高度)` 同源，缩到装不下子块为止；旧实现的高度恒用 `canvasMinHeight`、与子块数量无关，子块一多就能缩到「子块溢出画布区」）。改尺寸**不重排子块**：宽度变化只让子块横向重新居中（`childXOfNodeWidth`，x 是自动轴），高度变化保留用户摆放的 y（旧实现把画布区高度也放进 `watch` 的触发键里，拖拽把手每帧都会把用户拖走的子块拽回居中位置）。画布区以 `top/bottom` **锚定**在卡片内随尺寸伸缩，标记锚定画布区左 / 右缘并**固定在画布区垂直中点**、内侧 handle 放在**标记元素内部**（DOM 锚定）——因此改尺寸时标记、连接点与内部连线自动跟随，组件里没有写死的标记偏移。初始尺寸由 `expandIteration` 按 `containerSizeOf(子块数, 顶部内容区高度)` 写入节点 `style`（这里的顶部高度取 `estimatedTopAreaOf` 的估算值 —— 加载时还没有 DOM 可测，估算值是实测值的上界，因此只作下限、恒安全）；该尺寸只存在前端节点 `style` 里（提交后端时被字段白名单剔除，后端 schema 亦无 width / height 字段），**重新加载定义会回到初始尺寸**。顶部内容区高度（标题栏 + 三组变量行）**以实际渲染为准**：组件用 `ResizeObserver` 量 `.node-content` 的 `borderBoxSize`（布局尺寸，不受画布 `transform: scale()` 影响），而**不是**按变量个数推算 —— 变量胶囊是 `flex-wrap`，一行能并排多个，「变量个数」≠「渲染行数」。实测高度变化时按同一个 delta 调整节点盒高度（`cardHeightAfterContentChange`），使画布区高度（`canvasHeightOfCard` = 盒高 − 上下边框 − 顶部内容区高度 − 内缩）**守恒**：同一行内增删变量时卡片不长高、画布区也不动；首帧用 `estimatedTopAreaOf`（按变量个数算，是实测值的上界）兜底，挂载后最多小幅收敛一次。`layoutIteration` 同样接受实测值（子块增删时由组件传入），未传时才退回估算值。外侧连接点（`in` / `out`）的 y = `cardAnchorYOf(顶部内容区高度)`（挂在卡片左右边缘、与顶部内容区中线对齐，而不是整张卡片的中点）。两个标记只是**视觉元素、不是画布节点**，迭代体内部连线由挂在它们边缘的内侧 handle（`iterate-in` / `iterate-out`）承载，因此折叠回后端时不会多出节点。**子块的拖拽范围与可见画布区一致**：节点的 `extent` 由 `iterationChildExtent(顶部内容区高度)` 生成（`{ range: 'parent', padding: [上, 右, 下, 左] }`，四向都加上卡片边框宽度，因为 extent 的基准是父节点 border box 而画布区锚定在它的 padding box 内），顶部内容区高度变化时由组件 `watch(topAreaHeight)` 同步刷新；因此子块不会被拖到卡片头部 / 变量行上，也不会贴到卡片边缘（此前用的 `extent: 'parent'` 约束基准是**整张卡片盒**）。内侧 handle 与其它节点的连接点**同一套样式**：白底 + `--tb-border-strong` 边框、悬停卡片时淡入（`\.node-card:hover`）、按 type 带 `node-handle-source` / `node-handle-target` 修饰类，不再单独配色。**标记与子块之间的间距就是这段内部连线的长度**（`ITERATION_LAYOUT.markerGap` = 24px），它同时决定连线在画布上是否**可点**：太短（曾为 12px，屏幕上只有几个像素）时连线看不清、也点不中——用户想删掉它总是点到层级更高的子块卡片上（反而打开节点抽屉），于是「删不掉内部连线」、再连接时又被告知「已有连线」；**内部连线的可点性只能靠它本身的长度**（`ITERATION_LAYOUT.markerGap`），**不能**靠加宽命中区：Vue Flow 给每条边渲染的透明命中线（`interactionWidth`）两端正好就是这两个内侧 handle，而连线层级高于节点卡片，命中区一旦覆盖到端点，这两个连接点就再也拖不出连线（复现脚本：`frontend/.repro/repro-inner-handle-blocked.ps1`，判定 `blockedCount > 0`）；悬停 / 选中时线会加粗（`stroke-width: 3 !important`，覆盖行内样式）以便看清点中的是哪条，因此「点选连线 → Delete」与「删除后再连接」都能正常完成，重复连接按**节点对**判重（刻意忽略 handle —— 迭代体内部连线来自定义时两端不带 handle，手连时带内侧 handle，只看 handle 会叠加出语义重复的连线），提示会引导先删掉已有连线。迭代体内部连线的样式（含层级）由 `ITERATION_EDGE_STYLE` 提供：它带 `zIndex`，因为 Vue Flow 默认把连线渲染在节点层**之下**，而迭代节点是不透明卡片——不抬高层级，内部连线会被卡片底色整段盖住、完全看不见。层叠顺序由 `ITERATION_LAYER` 统一定义为「父卡片（默认 0）< 内部连线（1）< 子节点卡片（2）」：内部连线要高过父卡片才可见，子节点又要高过内部连线，否则连线会画在子块卡片上面。为避免选中节点时 Vue Flow 把该节点抬到 z-index 1000、打乱这个顺序，画布页显式关闭了 `elevateNodesOnSelect`。
- **进度**：结果面板上方的进度行展示「第 n/N 轮」与进度条；**n 取事件里携带的权威轮次下标**——迭代节点每轮把轮次下标写进 `state[迭代节点id].index`，转发出去的子块 `node` 事件同样带着它，前端按它取「已完成轮次」（`utils/run-state` 的 `iterations` 段，`Math.max` 取大，同一轮收到多个子块事件也不会重复计数）。事件里没有该下标时（兼容旧后端）退回「每收到一次子块事件算一轮」，含 N 个子块时会把轮次算成 N 倍，仅作兜底。N 取迭代源（`inputs` 里 list 那项的引用）的项数，口径与后端 `NodeStateReader#toList` 一致（数组取长度、`null` 取 0、其余标量 / 对象按单项列表算 1 轮），迭代源产出尚未到达时只显示已完成轮次；兜底累加得到的轮次会被钳到总轮数，文案不会出现「4/2 轮」。标题栏徽标优先显示轮次，未运行时退化为产出变量个数。画布只展开一层迭代体，内层嵌套迭代节点拿不到子块事件，因此不登记、也不展示轮次进度（见 §4.5）。
- **画布区内节点不单独展示**：挂在迭代节点画布区内的子块不渲染结果面板（前端 `utils/use-nested-node` 按「父容器**仍然存在**」判断——容器被删除后残留的悬空子块不再视为迭代体内节点，结果面板照常渲染，避免出现「面板永不出现」的静默现象；子块保持静态样式），迭代体各轮的产出由迭代节点的汇总结果统一体现。
- **状态徽标**：8px 圆点 + 状态文案；`waiting` 时附带 `label`。
- **产出变量**：按 `state[节点id]` 的键值逐行展示「变量名 = 取值」；字符串超过 60 字符截断，数组展示条数与前两项预览，对象展示字段数与前三个字段名；点击变量行可展开完整 JSON。
- **空态**：该节点没有产出（`state[节点id]` 缺失或为空）时展示「本次无产出变量」。当前 `EndNode`、`HumanNode` 的 `apply` 仍返回空 Map，因此这两类节点默认就是空态；`LlmNode` 已按 `outputs` 声明返回模拟产出，`IterateNode` 会按声明写入（可能为空的）结果列表（见 §7）。

---

## 3. 工作流定义 JSON 规范

### 3.1 根结构

工作流定义的根对象即 `WorkflowProcessRespVO`：

```json
{
  "nodes": [],
  "edges": []
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nodes` | `WorkflowNode[]` | 是 | 节点列表，缺省时为空数组 |
| `edges` | `WorkflowEdge[]` | 是 | 节点之间的连线，缺省时为空数组 |

反序列化约定：`WorkflowProcessLoader` 的 `ObjectMapper` 关闭了 `FAIL_ON_UNKNOWN_PROPERTIES`，且各实体均标注 `@JsonIgnoreProperties(ignoreUnknown = true)`。因此画布导出的额外字段（如 `meta`、`versions`、`nodeMeta`）会被**静默忽略**，不报错——但这不代表这些字段有语义，规范定义以本章字段表为准。

图装配规则（`WorkflowController#createGraph`）：`nodes` 逐个 `addNode(id, action)`，`edges` 按 `source → target` 连边；**无入边的节点自动接 `StateGraph.START`，无出边的节点自动接 `StateGraph.END`**。因此定义里不需要（也不应）显式声明 START / END 节点。

### 3.2 节点对象 `WorkflowNode`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | String | 是 | 节点唯一标识，同时作为状态命名空间；新版为数字串（如 `100001`、`132996`），旧版为语义化名称（如 `start`、`llm-plan`） |
| `type` | String | 是 | 节点类型编码，取值见 §4 |
| `position` | `{"x": Double, "y": Double}` | 否 | 画布坐标，仅前端渲染使用，后端不参与任何逻辑 |
| `data` | `NodeData` | 否 | 节点业务数据（名称、提示词、输入输出定义），见 §3.4 |
| `blocks` | `Object[]` | 否 | 迭代体子节点定义（`WorkflowNode` 结构），默认 `[]` |
| `edges` | `Object[]` | 否 | 迭代体内部连线（`WorkflowEdge` 结构），默认 `null` |

### 3.3 连线对象 `WorkflowEdge`

| 字段 | 类型 | 必填 | 可写别名（`@JsonAlias`） | 说明 |
| --- | --- | --- | --- | --- |
| `source` | String | 是 | `sourceNodeID`、`sourceNodeId`、`source` | 起点节点 id |
| `target` | String | 是 | `targetNodeID`、`targetNodeId`、`target` | 终点节点 id |
| `id` | String | 是 | — | 连线唯一标识 |

- **推荐写法**：新版画布统一使用 `sourceNodeID` / `targetNodeID`（含迭代节点内部的 `edges`），后端通过 `@JsonAlias` 兼容旧版的 `source` / `target`。
- 连线两端必须是已声明的节点 `id`（迭代体内部连线允许一端是迭代节点自身，见 §4.5）。
- 每条边必须声明非空 `id`，且**全图唯一**（顶层边与迭代体内部边共用同一命名空间）；加载时校验，缺失或重复会直接失败。命名约定 `e-{source}-{target}`，如 `e-100001-132996`。
- `id` 仅作标识：图装配只使用 `source` / `target`，`id` 不参与流转计算。
- **边级 `type` 不属于定义字段**：后端不识别；画布导出中若残留（Vue Flow 渲染属性），按未知字段静默忽略。
- **画布渲染必须显式指定 handle**：顶层边在前端会显式带上 `sourceHandle: 'out'` / `targetHandle: 'in'`（由画布页的 `toCanvasFlow` 补齐，已有 handle 的边保持原值），handle 不参与提交给后端的定义（`buildRunDefinition` 只取 `id` / `source` / `target`）。原因是迭代容器有 4 个 handle（外侧 `in` / `out` 与内侧 `iterate-in` / `iterate-out`），而 Vue Flow 对**未指定 handle 的边**会取「候选列表第一个」（`loose` 模式下候选会把 source / target 两侧合并），若不显式指定，主链路会连到迭代体的入口 / 出口上。同理，容器内的子块只能与同一容器内的节点（含容器自身）连线，跨越迭代体边界的连线会被画布拦截。

### 3.4 节点数据 `NodeData`

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `label` | String | — | 节点展示名称，人工确认中断的 `label` 即取自该字段 |
| `system` | String | — | 大模型节点的 system 提示词 |
| `user` | String | — | 大模型节点的 user 提示词 |
| `inputs` | `NodeField[]` | `[]` | 输入变量定义（含来源引用） |
| `variableParameters` | `NodeField[]` | `[]` | 迭代节点的中间变量，在迭代体每轮可见 |
| `outputs` | `NodeField[]` | `[]` | 输出变量定义 |

### 3.5 变量定义 `NodeField`

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `name` | String | — | 变量名（本地别名） |
| `type` | String | — | 变量类型：`string` / `list` / `object` 等 |
| `required` | Boolean | — | 是否必填 |
| `description` | String | — | 变量说明 |
| `schema` | Object | `{}` | 复杂类型的元素结构，如 `{"type": "string"}` |
| `input` | `NodeFieldRef` | — | 变量来源引用，`inputs` / `variableParameters` 使用 |

### 3.6 来源引用 `NodeFieldRef`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `blockID` | String | 来源节点 id（即产出该变量的 `WorkflowNode.id`） |
| `name` | String | 来源节点上声明的变量名 |
| `type` | String | 变量类型：`string` / `list` / `object` 等 |

约定：

- `outputs` 元素的类型与结构由 `type` + `schema` 描述；`inputs` 元素**可以只写 `name` + `input`**，此时类型从 `input.type` 读取（`IterateNode` 判定 list 即同时看 `field.type` 与 `field.input.type`）。
- `list` 类型的 `schema` 描述**元素**结构，而非整个数组：`{"type": "list", "schema": {"type": "string"}}` 表示字符串列表。
- 来源引用采用扁平结构 `{blockID, name, type}`；前端某些 Coze 风格画布导出的嵌套结构（如 `input.value.ref.content`）**与后端实体不同构**，不得直接作为工作流定义提交。

---

## 4. 节点类型规范

### 4.1 类型映射

`NodeActionFactory` 在匹配前对 `type` 执行 `trim()`，首尾空格不影响匹配；**类型只认数字编码**，不再兼容旧的语义化写法。

| `type` 取值 | 语义 | 实现类 | 行为现状 |
| --- | --- | --- | --- |
| `1` | 开始 | `StartNode` | 把业务输入写进 `state[节点id]`：按 `data.outputs` 声明**直读状态顶层同名键**（`POST /chat` 的 `inputs` 经 `ChatServiceImpl#initialState` 并入）；required 变量缺失直接抛异常（**无演示兜底**），非 required 缺省给类型空值（见 §4.4） |
| `2` | 结束 | `EndNode` | `apply` 返回 `Map.of()`：它是「最终返回物」的声明位，**当前不产出任何东西**（TODO，见 §4.4） |
| `3` | 大模型 | `LlmNode` | 读取 `inputs` → 渲染 `{{变量}}` 提示词 → 按 `outputs` 生成 JSON Schema → **调用真实模型**（模型可自主调 `read_homework_file_text`），把返回的 JSON 按变量名拆回 `state[节点id]`；只声明一个输出变量时，解析不出 JSON 会**静默退化成裸文本** |
| `4` | 迭代 | `IterateNode` | 按 list 输入逐项执行迭代体子图并转发子事件；按 `outputs` 声明把每轮子块的**同名产出聚合成列表**（变量名须与出口子块产出名一致），无输入或无子块时输出空列表 |
| `5` | 人工确认 | `HumanNode` | 进入节点前中断，等待 `resume` 反馈后继续；`apply` 把提交的 `values` 按 `state[节点id][变量名]` 回写，**空反馈时返回 `Map.of()`**（该节点的产出在状态里压根不存在，见 §4.3） |
| `6` | 输出 | `OutputNode` | **只声明 `inputs`**：把引用到的取值按变量名**原样透传**成自己的产出（详见 §4.6） |
| 其它 | — | — | 抛 `IllegalArgumentException("不支持的节点类型: " + type)` |

> 注意：**只认数字编码 `1`~`6`**。旧的语义化写法（`input` / `start` / `output` / `end` / `llm` / `iterate` / `human`）已移除，仍写字符串会落到 `default` 分支直接抛异常。
> 画布与后端的编码一一对应，且前端 `nodeTypes`（Vue Flow 的节点类型注册表）使用同一套字符串 key。

### 4.2 大模型节点的输出结构（JSON Schema）规范

`LlmNode#buildOutputSchema` 按 `outputs` 声明的变量个数生成模型结构化输出约束：

| `outputs` 声明 | 生成的 JSON Schema |
| --- | --- |
| 恰好 1 个（`name` 非空） | 直接使用该变量的 schema，模型输出的 JSON 就是该变量本身（**不额外包变量名**） |
| 多个 | `{"type": "object", "properties": {变量名: schema}, "required": [...]}`，仅 `required = true` 的变量进入 `required` |

单个变量的 schema 由 `fieldSchema` 生成：

| 变量声明 | 生成的 schema |
| --- | --- |
| `type: list` | `{"type": "array", "items": <schema 或 {"type":"string"}>}`，`items` 取自该变量的 `schema`，为空时默认字符串 |
| 其它 `type` | `{"type": <type>}`，`type` 缺省时回落 `"string"` |
| 带 `description` | 追加 `"description"` 字段 |

因此规范要求：**`list` 输出必须用 `schema` 描述元素结构**（否则模型只会拿到字符串数组）；`outputs` 只有一个变量时不要在提示词里要求模型包裹变量名。

### 4.3 人工确认节点

- 实现 `InterruptableAction`，进入节点**之前**中断；续跑时通过 `RunnableConfig.Builder.addHumanFeedback(...)`
  带入反馈，此时不再中断。
- **中断元数据即弹窗数据**（`HumanNode#interrupt` 一次算完，执行侧只负责搬运与推送）：
  | metadata 键 | 来源 | 结构 |
  | --- | --- | --- |
  | `nodeId` | 节点 id | 字符串 |
  | `label` | `data.label` | 弹窗标题 |
  | `data` | `data.inputs` 的引用逐个用 `NodeStateReader#read` 取值 | `[{name, type, label, value}]`——只读回显的**待确认数据** |
  | `fields` | `data.outputs` 声明 | `[{name, type, required, label, schema}]`——待用户填写的**表单字段** |
- 卡片帧把 `data` / `fields` 原样放进 `content`（见 §2.2），**字段清单只在这一处下发**
  （不像 Coze 散在 `input_card_data` / `variables` / `elements` 三处还得手工保持一致）。
- `fields[].type` 取值：`string` / `text`（文本）/ `list`（列表，元素结构由 `schema` 描述）/ `object`。
- **前端取值口径**（`views/workflow2/utils/card-fields.js`，纯函数、有单测）：按**变量名**把 `fields` 与 `data` 对齐，分三类：
  | 分类 | 判据 | 界面 | 提交 |
  | --- | --- | --- | --- |
  | 只读回填 | `fields` 里有、`data` 里也有同名项 | 只读（`data` 那段已在展示，不重复渲染） | **原值回填** |
  | 待填写 | `fields` 里有、`data` 里没有同名项、且是文本类 | 输入框 | 填什么提交什么 |
  | 填不了 | 同上、但非文本类（`list` / `object`） | 只读提示「需上游节点产出」 | 不提交，且**不参与必填拦截**（否则填不了又过不去＝死锁） |
- ⚠️ **`outputs` 与 `inputs` 同名的字段是「只读 + 原值回填」**：卡片上不出现输入框，老师只需点确定。
  这不是 UI 偏好，而是下一条引擎约束的产物；写错的表现是**流程照样成功、下游变量却全是空**，
  所以前端这条规则必须被单测钉住，不能内联在组件里随手改。
- ⚠️ **中断点之前各节点的产出，在续跑后的状态里不再存在**（实测：续跑轮的状态顶层键只剩中断点之后写入的那些，
  中断前的节点整体消失）。因此**凡在中断点之后要用到的变量，都必须由中断节点自身承载**：把它同时声明进
  `data.inputs`（决定卡片能回显什么）与 `data.outputs`（决定要提交什么），下游一律引中断节点的 id。
  直接引中断点之前的节点，会在续跑后静默取到 `null`（提示词里渲染成空串）。
- 列表类字段当前**没有可编辑控件**，也不再造模拟数据：真实场景里「人填列表」由 `POST /homework/upload`
  + 开始节点提供；要做结构化编辑器（如学生名单的增删改）是独立一件事。
- 续跑注入的状态键固定为 `__human_feedback__`（`ChatProtocol.HUMAN_FEEDBACK_KEY`），值为
  `{字段名: 取值}`；`HumanNode#apply` 把它按 `state[节点id][变量名]` 的口径回写，下游节点用
  `blockID + name` 即可引用。该键在 `WorkflowGraphFactory#keyStrategyFactory()` 里**显式注册**
  （`ReplaceStrategy`）——检查点合并会按策略表过滤，未注册的键会被静默丢弃。

### 4.4 开始 / 结束节点

- **开始节点**：按 `data.outputs` 的声明**直读状态顶层同名键**并写进 `state[节点id]` —— `POST /chat`
  请求体的 `inputs`（`ChatReqVO#inputs`）会被 `ChatServiceImpl#initialState` 并入初始状态。
  「产出哪些变量」与「取哪个键」都由声明决定（内置批改定义即 `file_tree` / `description` / `know`）。
  **没有演示兜底**：required 变量缺失（取不到、文本只有空白、列表为空）时 `apply` 直接抛
  `IllegalStateException`（消息列出缺失变量与提供途径），由上层转成 `chat.failed` 帧；
  非 required 变量缺省时按类型给空值（list → 空列表、string → 空串），保证产出变量在状态里存在、
  下游取键不会拿到缺失键。
  两条提供途径：**批改页**发起执行时随 inputs 自动携带（上传返回的 files + 面板里的批改要求与知识库）；
  **画布试运行**在运行前的「输入数据」弹窗（`StartInputsDialog`）按声明填写（list / object 用 JSON 编辑，
  上次填写按 workflowId 记忆）。
  与 `POST /homework/grade` 的口径：grade 只把批改要求与知识库**落库存档**（历史 / 管理端可查），
  **执行以 inputs 为准**——两处都能送值时 inputs 是唯一权威，grade 里的那份仅作存档。
  > 以 `__` 开头的输入键一律忽略：`__START__` / `__human_feedback__` 是引擎保留键，让请求体覆盖会破坏执行语义。
  > 续跑（`status = 4`）**不吃** `inputs`，只认 `message.values`，避免「两处都能送值、到底哪份算数」的歧义。
- **结束节点**：`apply` 返回空 Map，**当前不产出任何东西**。它的定位是**「最终返回物」的声明位**
  —— `inputs` 里声明将来要交付的东西（如 Excel 导出所需的数据），具体产出待实现（TODO）。
  页面展示用的结构由**输出节点**（§4.6）承担，**不靠结束节点**。

### 4.5 迭代节点

迭代源：取 `data.inputs` 中**第一个** `type = list`（或 `input.type = list`）的输入；没有 list 时退回第一个带 `input` 引用的输入；仍取不到则视为无可迭代项，节点仍按 `outputs` 声明写入空列表（而不是返回空 Map，避免下游取到 `null`）。

迭代源归一化（`NodeStateReader#toList`）：`List` 原样复制；数组按元素逐个取出（用反射，**原始类型数组 `int[]` 也支持**，旧实现 `(Object[])` 强转会抛 `ClassCastException`）；`null` 返回空列表（迭代源还没产出）；**其余标量 / 对象按单项列表处理**（旧实现直接丢弃成空列表，会让迭代体一轮都不跑且没有任何告警）。

迭代体装配（`blocks` + 节点级 `edges`）：

- `blocks` 反序列化为子节点，节点级 `edges` 反序列化为子边。
- 边里**以迭代节点自身 `id` 作为虚拟入口 / 出口**：`迭代节点id → 子块` 等价于子图 `START → 子块`；`子块 → 迭代节点id` 等价于 `子块 → END`。
- 未声明入口 / 出口的子块按入度、出度兜底补齐（只有一个子块时即 `START → 子块 → END`）。
- **出口节点的推导必须与上面这条兜底同源**：`collectTerminalBlockIds` 取「**没有出边的子块**」（出边指指向另一个子块的边；指向迭代节点自身的边表示接 `END`）。旧实现只认「指向迭代节点自身的边」，漏声明出口边时子图照常执行、但每轮结果收集恒为空（表现为「流程成功但没有结果」）。
- 子图独立编译、不挂 checkpoint，`input` / `output` 两个键使用 `ReplaceStrategy`（每轮状态独立，避免同名键被累积成列表）。

每轮注入的子图状态（命名空间为迭代节点 `id`）：`index`（轮次下标）、`item`（当前项）、当前项别名（子块引用迭代节点 `id` 时声明的 `name`，如 `input`），以及 `variableParameters` 声明的中间变量。

汇总：`aggregate()` 按 `outputs` 声明的变量名，从每轮结果（`collectRoundResult`，取迭代体出口节点的产出）里取同名变量聚合成列表；**失败轮次按 `null` 补齐**（结果列表长度始终等于执行的轮次数，界面上该轮显示「空值」，不会像旧实现那样悄悄变短），成功轮次里本来就没有该变量时仍跳过该轮。**迭代节点的 `outputs` 变量名要与迭代体子块的产出变量名一致**，否则汇总为空列表。没有可迭代项或没有迭代体子块时，同样按声明写入空列表（而非缺省返回空 Map），保证下游取变量不会拿到 `null`。

单轮失败与轮数上限：某一轮抛异常时记 ERROR 日志（含堆栈）并把该轮产出按 `null` 补齐；**所有轮次都失败**时整节点抛异常，由上层把流程标记为失败（旧实现只打印一行 `System.out` 后继续，前端看到的是「完成」而结果为空）。单次执行最多 `tb.workflow.iterate.max-rounds`（默认 50）轮、单轮最长 `tb.workflow.iterate.round-timeout-ms`（默认 120000 ms），超出上限的轮次不再执行并记 WARN。迭代体是**串行**执行（`block()`），轮数上限即单次执行耗时的上界。

嵌套迭代体：`expandIteration` 只展开**一层**——迭代体里再放迭代节点时，内层 `blocks` / `edges` 原样留在子块定义里（提交后端不丢数据，`expand → collapse` 往返一致），但内层子块不在画布上展开，卡片上以「嵌套 N」徽标标明。**内层容器因此拿不到任何画布子块事件**，前端不登记它的轮次进度（`utils/run-state#iterationPlanOf` 跳过没有画布子块的迭代节点），内层卡片不会显示永远不动的「0/N 轮」，只以「嵌套 N」徽标标明。

日志：迭代节点与大模型节点的日志只记节点 / 子块 `id` 与变量名（DEBUG 级），用户填写的节点定义、渲染后的提示词、节点产出都不进日志（`/workflow/text` 只记终态的状态键）。

### 4.6 输出节点

**它声明「要展示的结果」，不搬运、不加工。**

- **只声明 `inputs`，没有 `outputs`**：产出名就是这些 `inputs` 的变量名（`OutputNode#apply` 遍历 `inputs`
  的引用、用 `NodeStateReader#read` 取值、按变量名放进自己的命名空间 —— 与 `HumanNode#apply` 回写提交值同一范式）。
  再引一层 `inputs → outputs` 映射就要定义"哪个 input 喂哪个 output"的对应关系与校验，而这里没有新信息可加，
  所以刻意不做。
- **取不到值就是 `null`，不抛异常**（与其它节点一致）：上游没产出时静默为 null，由运行前校验
  （`utils/run-validation`）在编辑期报「引用取不到值」。
- **可以有多个**，不限制唯一。它出现的位置决定了它的语义：

| 位置 | 语义 | 产出在哪里 | 页面/调用方怎么读 |
| --- | --- | --- | --- |
| **迭代体内**（`blocks` 里，作为迭代体出口） | 声明「**单份**要展示什么」 | 不出现在父状态的 `state[输出节点id]` 里（子图状态独立），而是被 `IterateNode` 按**同名**聚合到**迭代节点**上，每个字段变成一个列表 | 从**迭代节点**的产出里按同名取；`frontend/src/views/homework/utils/result-contract.js` 的 `recordsOf` 按**下标**把这些平行列表拼成逐条记录 |
| **顶层** | 声明「**整批**要展示什么」 | 直接挂在 `state[输出节点id]` | 从该输出节点自己的产出取 |

- **结束节点不承担展示**：它只当"最终返回物"的声明位（§4.4）。当前内置的「标准批改作业」把输出节点放在
  迭代体内 —— 于是页面展示的是「迭代节点的输出列表」，逐学生一条。

> **页面的读取契约**（`frontend/src/views/homework/`）：选中工作流后读一次定义，用
> `resultContractOf(schemaJson)` 找输出节点（**顶层优先**，其次迭代体内的第一个）→ 拿 `fields`
> （`inputs` 的 `name` 作字段名、`description` 作标签）；数据来源是 `sourceNodeId`
> （体内输出节点时就是它所属的迭代节点）。找不到输出节点时**不提交执行**并提示用户。

---

## 5. 变量取值与引用约定

1. **状态命名空间**：统一为 `state[节点id][变量名]`（由 `NodeStateReader#read` 实现：先取 `state.value(blockID)`，再从结果 Map 中取 `name`）。因此节点 `apply` 的返回值必须包一层 `{ 节点id: { 变量名: 值 } }`，例如 `StartNode` 返回 `Map.of(definition.getId(), objectMap1)`。
2. **取不到的语义**：来源节点未产出、产出不是 Map、字段不存在，三者均返回 `null`，不抛异常。
3. **来源声明**：`inputs` / `variableParameters` 元素通过 `input.blockID` + `input.name` 指向产出方，禁止用提示词占位符直接当变量来源。
4. **提示词占位符**：`{{变量名}}`，匹配正则 `\{\{\s*([\w.\-]+)\s*}}`（允许花括号内空格）；变量名取 `inputs` 的**本地别名**（`NodeField.name`）；取不到值（含模板里写了未声明的变量）时替换为空串，避免占位符原样发给模型。
5. **状态键策略表**（`WorkflowGraphFactory#keyStrategyFactory()`）：空串键 `""` → `AppendStrategy`（源码注释称「变量类型为 `list` 时视为可累积的记忆变量」），`__human_feedback__` → `ReplaceStrategy`（人工反馈注入）。人工反馈这个键**必须显式注册**——检查点合并时 `OverAllState.input(checkpointState)` 会按策略表过滤，未注册的键会被静默丢弃，那样续跑注入的填写值就到不了人工确认节点。其余键走引擎默认策略。
6. **迭代节点输出**：以自身 `id` 为命名空间，按 `outputs` 声明的变量名写出。

---

## 6. 定义加载与落库

### 6.1 定义加载：按 id 读库 + classpath 回落

| 项 | 现状 |
| --- | --- |
| 加载入口 | `WorkflowService#getProcess(String workflowId)` |
| 入参处理 | **数字 id → 读 `workflow` 表的 `schema_json`**（记录不存在 / JSON 非法 / 连线 id 缺失重复 → `code = 400`）；非数字 id（联调用的 `"text"`）→ classpath `text.json` |
| 调用点 | `/chat` 按会话绑定的 `workflow_id` 调用；`/workflow/text` 硬编码 `"text"` |
| 缓存 | classpath 回落路径仍用 `volatile` 字段 + 双检锁缓存，进程内**首次读取后不再读盘**（改文件需重启）；读库路径每次都查库，改定义立即生效 |
| 失败行为 | 读库失败抛业务异常（`code = 400`，`message` 可直接展示）；classpath 读取失败抛 `IllegalStateException("加载工作流定义失败: text.json")` |
| 其它入口 | `WorkflowProcessLoader#loadFromClasspath(location)` / `#loadDefault()` / `#parse(json)` |
| `text2.json` | 存在于 `resources/`，**代码未引用** |

### 6.2 定义持久化：`workflow` 表（已实现）

表结构见 `backend/sql/workflow.sql`，读写由 `WorkflowDefinitionRepository`（`JdbcTemplate`，单表，全部查询带 `deleted = FALSE`）承担，对外经三个接口暴露（见 §2.1 的接口 4~6）。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` PK | 雪花 ID（`IdUtil.getSnowflakeNextId`），由应用生成，非数据库自增 |
| `name` | `VARCHAR(100)` NOT NULL | 流程名称 |
| `description` | `VARCHAR(500)` | 流程说明 |
| `schema_json` | `TEXT` NOT NULL | 流程定义 JSON（`{nodes, edges}` **整体存入**，不拆节点 / 连线子表；迭代体 `blocks` / `edges` 原样保真） |
| `status` | `SMALLINT` NOT NULL DEFAULT 0 | 0 = 草稿，1 = 已发布 |
| `visibility` | `SMALLINT` NOT NULL DEFAULT 0 | 可见性：0 = 私有，1 = 公有（ADR 0006 归属模型；新建默认私有，存量经 `visibility_creator_backfill_20261116.sql` 刷 0 并补齐 NULL creator 为 admin 种子账号） |
| `create_time` / `update_time` | `TIMESTAMP` NOT NULL DEFAULT `now()` | 创建 / 更新时间 |
| `creator` / `updater` | `BIGINT` | 创建人 / 更新人（`system_user.id`，逻辑外键，不建物理约束） |
| `deleted` | `BOOLEAN` NOT NULL DEFAULT `FALSE` | 软删 |

已实现约定：

- **保存口径**：`id` 为空走新建（应用生成雪花 ID），非空走更新；`schema_json` 整体存入，**不做版本管理**，保存即覆盖当前记录。
- **校验**：`name` 必填且 ≤100 字符，`description` ≤500 字符，`schemaJson` 必须是合法 JSON 且连线 `id` 非空、全图唯一（复用 `WorkflowProcessLoader#parse`）；失败返回 `code = 400`，`message` 可直接展示。
- **id 全程按 String 传递**：19 位雪花 ID 超出 JS 安全整数范围，入参与出参的 `id` 都是字符串（`WorkflowSaveReqVO#id` 与各 VO 的 `id` 字段均为 `String`），仓库层写入时用 `CAST(? AS BIGINT)` 显式转换。
- **列表不返回 `schemaJson`**（画布可能很大），但服务端会读取它统计 `stepCount`；`WorkflowVO` / `WorkflowDetailRespVO` 都带 `builtin`（是否内置模板，按固定 id 判定）与 `stepCount`（顶层节点数，迭代体子块不计入），供前端下拉分组与「N 个步骤」展示。
- **内置模板**：`WorkflowDefinitionInitializer`（`ApplicationRunner`）在表内**无未删除记录**时，把 `resources/workflow/standard-grading.json` 写入为一条固定 id（`1000000000000000001`）的「标准批改作业」记录；幂等——表里有记录即跳过，不覆盖用户数据，初始化失败只告警、不阻断启动。**注意：改种子文件不会更新已存在的库**，生效方式见 [specs/2026-10-14-批改作业工作流定义替换.md](./specs/2026-10-14-批改作业工作流定义替换.md) §6；该模板的定义内容与结构同文 §3。
- **与执行链路的关系**：`WorkflowServiceImpl#getProcess`（第 60~68 行）对**数字 id 读 `workflow` 表的 `schema_json`**（读库路径每次都查库，改定义立即生效），只有非数字 id（联调用的 `"text"`）回落 classpath `text.json`（该路径进程内首次读取后缓存，改文件需重启）。因此**运行时的定义以库内记录为准**。
- 索引：`idx_workflow_name ON workflow(name) WHERE deleted = FALSE`（**非唯一**，fork 副本命名防撞靠应用层循环 `selectIdByName`，重试上限 99 次）。
- 脚本使用 `CREATE TABLE IF NOT EXISTS`，不会重建已存在的旧结构表；旧表为旧画布格式，与当前结构不同构。

### 6.3 归属与共享（2026-11-16 已实现，ADR 0006）

> 工作流从「全局池」改为「按人归属 + 公有共享」：决策依据与取舍见 [adr/0006](./adr/0006-知识库与工作流归属与共享模型.md)。

| # | 能力 | 现状实现 |
| --- | --- | --- |
| 1 | 列表 creator 过滤 | `listWorkflows` / 首页卡片 / 批改页工作流下拉共用 `selectListVisible(creatorId)`：登录用户只见**自己的 + builtin**；匿名（`/workflow/**` 免登录白名单是 URL 级现状，URL 模式无法按方法拆分）只查 `SELECT_LIST_BUILTIN_ONLY`，**看不到任何私有流**——列表过滤在查询侧完成，即数据安全边界 |
| 2 | 可见性切换 | `PUT /workflow/visibility`：仅创建者（非创建者 `code = 403`）；builtin 固定 id 同样拒绝；`visibility` 取值非 0/1 按 400 拒绝 |
| 3 | detail 归属下发 | `/workflow/detail` 返回 `visibility` / `mine`（当前登录用户是否创建者）/ `builtin`；画布页 `!mine && !builtin` 即只读形态（连线、删节点、加节点、保存全部禁用，运行按钮提示 fork），**前端不猜归属** |
| 4 | 直跑闸 | `ChatServiceImpl.createConversation` 与 `chat()` **双入口**过 `assertWorkflowRunnable`：`own OR builtin` 才放行，否则 `code = 403` 提示「请先 fork 为自己的副本」（见 §2.1 强制约定） |
| 5 | 公有库浏览区 | `GET /workflow/public-list`：`visibility=1` 且**非本人创建**（匿名时没有「本人」，返回全部公有行）；带 `creatorName`（JOIN system_user，nickname 缺失降级 username），**不回传 schemaJson** |
| 6 | fork | `POST /workflow/fork`：闸为 `own OR public`（内置模板**显式拒绝**——builtin 是系统资产，不产出个人副本）；新雪花 id，拷 `name`（「原名（副本）」起、重名追加「（副本 2）」、超长先截断基底再接后缀）/`description`/`schema_json`，副本 `status=0 草稿`、`visibility=0 私有`、`creator=当前用户`；fork 成功后前端直接打开副本（`mine=true` 即刻可编辑可运行，完成「添加为自己的」闭环） |

**现状记录**：`/workflow/public-list` 对匿名开放（白名单现状 + 查询侧过滤的推导结果）；`workflow.name` 无唯一索引，fork 副本命名循环只是整洁性措施（重名也能插入成功）。

---

## 7. 现状 / 规范要求 / 待整改

本节如实记录与既有文档描述的偏离，**不美化**。已整改项只保留一行（标明「已整改」），不再展开。

| # | 位置 | 现状 | 规范要求 | 待整改动作 |
| --- | --- | --- | --- | --- |
| 1 | 接口集合 | 执行入口已**合并为 `/chat`**（+ `/chat/conversation` 建会话、`/chat/conversation/list` 会话列表），旧 `POST /workflow/run` / `/workflow/resume` 已废弃；`GET /workflow/get_process` **从未实现，已确定不做**（终态只认 SSE 帧，见 §2.2） | — | **已整改**：[doc-api.md](./doc-api.md) §2.5、[doc-conventions.md](./doc-conventions.md) §2.6、[doc.md](./doc.md) §4 已按 `/chat` 口径同步，不再把 `get_process` 写成规划项 |
| 2 | 定义来源 | **已整改**：数字 id 读 `workflow` 表 `schema_json`，非数字 id 回落 classpath（见 §6.1） | 按 §6.2 的 `workflow` 表读取并回落文件 | — |
| 3 | SSE 事件名 | **已整改**：`conversation.chat.created` / `.in_progress` / `conversation.message.completed`（verbose / card / text）/ `.requires_action` / `.completed` / `.failed` / `done`，对齐 Coze 命名并裁掉冗余信号 | [doc-conventions.md](./doc-conventions.md)、[doc-api.md](./doc-api.md) 描述 `created` / `message` / `completed` / `error` / `done`、`requires_action` | **已整改**：两份文档已改为 §2.2 的事件名口径 |
| 4 | 执行状态与轮询兜底 | 状态码已落地（`workflow_run.status`：0 待运行 / 1 执行中 / 2 成功 / 3 失败 / 4 中断 / 5 已取消，其中 `0` 与 `5` 一期未使用），但**仍无状态查询接口**；`/chat` 同步执行、无执行线程池 | 终态判定只依赖 SSE 帧；断线 / 刷新后不回溯执行状态 | 若要做「刷新后恢复执行状态」，需实现按 `execute_id` / `conversation_id` 的状态查询接口（一期明确不做，[05](./doc-api.md)/[08](./doc.md) 已按无兜底口径记录） |
| 5 | 大模型节点 | **已整改**：`apply` 调用真实 `ChatClient`（`nodeChatClient.prompt().system().user()`，可挂 `toolCallbacks`），返回文本按 `outputs` 声明拆成 `{变量名: 值}` 写回状态；未配置模型客户端时抛「未配置对话模型客户端」，返回为空同样报错 | 调用模型并将结果写入 `state[节点id][outputs 变量名]` | 收尾项：`LlmNode.apply` 里残留一行 `System.out.println(output)` 待删 |
| 6 | 结束节点 | `apply` 返回 `Map.of()`（TODO） | 组装最终返回内容 | 实现最终输出组装；在此之前 `content_type: text` 的「最终答案」帧不发 |
| 7 | 迭代节点汇总 | 已按 `outputs` 声明聚合各轮子块同名产出（空轮次跳过） | 按 `outputs` 汇总各轮真实结果 | 已整改：`aggregate()` 按变量名聚合（`outputs` 变量名须与子块产出名一致） |
| 8 | 人工确认节点输出 | **已整改**：续跑注入的 `__human_feedback__` 按 `state[节点id][变量名]` 回写（见 §4.3） | 读取人工反馈并写入声明变量 | — |
| 9 | 卡片与答案的落库 | 卡片 / 答案 / 用户提交**只走 SSE**，不落库 | 前端「刷新后能回显中断卡片与历史」 | **已接受的取舍**：刷新 / 换设备后卡片不可回显；要做回显需新增会话消息表（一期明确不做，见 §2.5） |
| 10 | `message.delta` | 保留事件名但**不发送** | 逐字流式输出 | 接入真模型后再发；前端已按「有 delta 就逐字渲染、没有就整段渲染」实现 |
| 11 | 中断的持久性 | 中断点（`interruptions`）与 `MemorySaver` 检查点都只存内存 | 跨进程重启可续跑 | **已接受的取舍**：服务重启后该会话续跑返回 400，需重新发起执行 |
| 12 | 归属共享的权限落点 | `/workflow/**` 整体在免登录白名单（URL 级），新加的 `/visibility`、`/fork`、`/public-list` 因此**匿名可达**；归属与直跑校验全部做在 **Service 层**（未登录 `currentUserId() == null` → 401 / 非本人 → 403），`/chat` 与 `/chat/conversation` 本身要求登录 | 权限边界应在服务端成立而非依赖 URL | **现状可接受**：写路径（visibility/fork）匿名必然 401；public-list 匿名可见是 §6.3 记录的现状。若未来要按方法收口白名单，属独立变更 |

---

## 8. 附录：工作流定义示例

### 8.1 各类型节点示例

以下片段均为**单个节点对象**，插入根级 `nodes` 数组即可使用；完整可加载定义见 §8.2。

- `id` 必须全图唯一（根级边与迭代体内部边共用同一命名空间）；`type` 只认数字编码（`1`~`5`），匹配前会 `trim()` 首尾空格。
- 边在根级 `edges` 或迭代节点的 `edges` 中声明，**每条边都必须带 `e-{source}-{target}` 形式的 `id`**。
- `position` 仅前端渲染使用；普通节点的 `blocks` 写 `[]`、`edges` 写 `null`。

**开始节点（`type`：`1`）**

```json
{
  "id": "100001",
  "type": "1",
  "position": { "x": 120, "y": 120 },
  "blocks": [],
  "edges": null,
  "data": {
    "label": "开始",
    "outputs": [
      {
        "name": "file_tree",
        "type": "list",
        "required": true,
        "description": "文件树（列表）",
        "schema": { "type": "object" }
      },
      {
        "name": "description",
        "type": "string",
        "required": true,
        "description": "AI 批改描述"
      }
    ]
  }
}
```

- 类型写数字 `1`（旧写法 `input` / `start` 已移除）。
- 无入边的节点会自动接 `StateGraph.START`，无需显式声明入口（§3.1）。
- 产出挂在 `state[100001][变量名]`（§5）。
- **现状**：`StartNode#apply` 写入的是硬编码演示数据（`file_tree`、`description`、`know`），**与 `data.outputs` 声明无关**（§4.4）。

**结束节点（`type`：`2`）**

```json
{
  "id": "900001",
  "type": "2",
  "position": { "x": 2000, "y": 120 },
  "blocks": [],
  "edges": null,
  "data": {
    "label": "结束",
    "inputs": [
      {
        "name": "input",
        "input": { "type": "string", "blockID": "100001", "name": "description" }
      }
    ]
  }
}
```

- 类型写数字 `2`（旧写法 `output` / `end` 已移除）。
- `inputs[].input` 用扁平结构 `{blockID, name, type}` 指向产出方（§3.6）；无出边的节点会自动接 `StateGraph.END`。
- **现状**：`EndNode#apply` 返回 `Map.of()`，最终内容组装待实现（§4.4）。

**大模型节点（`type`：`3`）**

```json
{
  "id": "100002",
  "type": "3",
  "position": { "x": 800, "y": 120 },
  "blocks": [],
  "edges": null,
  "data": {
    "label": "作业规划",
    "system": "<role>你是一个教务作业批改辅助系统，从文件树中判断出需要批改与登记成绩的学生名单。</role>",
    "user": "## 用户描述\n{{description}}\n\n## 文件树\n{{file_tree}}\n\n请依据以上信息输出规划 JSON。",
    "inputs": [
      {
        "name": "description",
        "input": { "type": "string", "blockID": "100001", "name": "description" }
      },
      {
        "name": "file_tree",
        "input": { "type": "list", "blockID": "100001", "name": "file_tree" }
      }
    ],
    "outputs": [
      {
        "name": "student_categories",
        "type": "list",
        "required": true,
        "description": "学生分类（列表）",
        "schema": {
          "type": "object",
          "properties": {
            "student": { "type": "string", "description": "学生姓名" },
            "paths": { "type": "array", "items": { "type": "string" }, "description": "该学生对应的文件路径列表" }
          },
          "required": ["student", "paths"]
        }
      }
    ]
  }
}
```

- 类型写数字 `3`（旧写法 `llm` 已移除）。
- `system` / `user` 中的 `{{变量名}}` 取 `inputs` 的**本地别名**（`NodeField.name`），取不到值（含未声明的变量）替换为空串；提示词内的换行必须写成 `\n`（JSON 字符串不允许裸换行）。
- `outputs` 只有 1 个变量时，生成的 JSON Schema 就是该变量本身，**模型输出不包变量名**；`list` 输出必须用 `schema` 描述元素结构（§4.2）。
- **现状**：`LlmNode#apply` 尚未调用真实模型，按 `outputs` 声明生成模拟产出（`list` → 4 个元素，元素按 `schema` 的 `properties` 逐字段构造；`string` → 模拟文本）（§4.1、§7 第 5 条）。

**迭代节点（`type`：`4`）**

```json
{
  "id": "132996",
  "type": "4",
  "position": { "x": 1400, "y": 120 },
  "data": {
    "label": "逐份批改",
    "inputs": [
      {
        "name": "output2",
        "input": { "type": "list", "blockID": "100001", "name": "file_tree" }
      }
    ],
    "variableParameters": [
      {
        "name": "jj",
        "input": { "type": "string", "blockID": "100001", "name": "description" }
      }
    ],
    "outputs": [
      {
        "name": "result",
        "type": "list",
        "schema": { "type": "string" },
        "required": true,
        "description": "逐份批改结果汇总（名称须与迭代体子块的产出同名，aggregate 按同名聚合成列表）"
      }
    ]
  },
  "blocks": [
    {
      "id": "173594",
      "type": "3",
      "position": { "x": 40, "y": 80 },
      "blocks": [],
      "edges": null,
      "data": {
        "label": "单份批改",
        "system": "<role>你是一个作业批改助手。</role>",
        "user": "## 批改要求\n{{jj}}\n\n## 当前作业\n{{input}}\n\n请输出该份作业的批改结果。",
        "inputs": [
          {
            "name": "input",
            "input": { "type": "string", "blockID": "132996", "name": "input" }
          },
          {
            "name": "jj",
            "input": { "type": "string", "blockID": "132996", "name": "jj" }
          }
        ],
        "outputs": [
          {
            "name": "result",
            "type": "string",
            "required": true,
            "description": "单份作业的批改结果"
          }
        ]
      }
    }
  ],
  "edges": [
    { "id": "e-132996-173594", "sourceNodeID": "132996", "targetNodeID": "173594" },
    { "id": "e-173594-132996", "sourceNodeID": "173594", "targetNodeID": "132996" }
  ]
}
```

- 类型写数字 `4`（旧写法 `iterate` 已移除）。迭代节点没有 `edges: null` 之外的写法，因为 `edges` 承载迭代体连线。
- 迭代源取 `data.inputs` 中**第一个 `list`**（此处 `output2`），类型写在 `input.type` 里亦可（§4.5）。
- 迭代体 = `blocks`（子节点）+ 本节点的 `edges`（子连线），**以迭代节点自身 `id` 作虚拟入口 / 出口**：`132996 → 子块` 等价于 `START → 子块`，`子块 → 132996` 等价于 `子块 → END`。
- 子块引用迭代节点时 `blockID` 写**迭代节点自身 id**（`132996`）：`input` 是每轮注入的当前项别名，`jj` 来自 `variableParameters`；此外每轮还会注入 `index`（下标）与 `item`（当前项）。
- 两条迭代体连线同样必须带全局唯一的 `id`。
- **现状**：汇总 `aggregate()` 已按 `outputs` 声明聚合各轮子块同名产出；**`outputs` 变量名须与子块产出名一致**，否则汇总为空列表（§4.5、§7 第 7 条）。

**人工确认节点（`type`：`5`）**

```json
{
  "id": "100003",
  "type": "5",
  "position": { "x": 1100, "y": 120 },
  "blocks": [],
  "edges": null,
  "data": {
    "label": "人工确认",
    "inputs": [
      {
        "name": "plan",
        "input": { "type": "list", "blockID": "100002", "name": "student_categories" }
      }
    ],
    "outputs": [
      {
        "name": "confirmedPlan",
        "type": "list",
        "required": true,
        "description": "人工确认后的清单"
      }
    ]
  }
}
```

- **类型固定为数字 `5`**：旧写法 `human` 已移除，写成字符串会落到 `NodeActionFactory` 的 `default` 分支抛 `IllegalArgumentException`（§4.1）。
- 进入节点**之前**中断，`data.label` 会作为 `chat.requires_action` 帧里的卡片标签；调用方**再请求一次 `POST /chat`**（同 `conversation_id` + 卡片勾选值）继续，旧写法 `POST /workflow/resume?run_id=&content=` 已废弃；反馈以 `confirmedPlan = [content]` 注入状态，并按 `state[节点id][变量名]` 回写（§2.3、§4.3）。
- **现状**：`HumanNode#apply` 返回 `Map.of()`，未把人工反馈写入 `outputs`（§7 第 8 条）。

### 8.2 综合示例（真实定义）

以下示例与 `backend/tb-module-workflow/src/main/resources/text.json` 结构一致（提示词已截断），可直接作为 `schema_json` 的形态参考：开始节点 → 迭代节点（迭代体内为大模型节点）→ 结束节点。

```json
{
  "nodes": [
    {
      "id": "100001",
      "type": "1",
      "position": { "x": 1460, "y": 120 },
      "blocks": [],
      "edges": null,
      "data": {
        "label": "开始",
        "outputs": [
          {
            "name": "file_tree",
            "type": "list",
            "required": true,
            "description": "文件树（列表）",
            "schema": { "type": "object" }
          },
          {
            "name": "description",
            "type": "string",
            "required": true,
            "description": "AI批改描述"
          }
        ]
      }
    },
    {
      "id": "900001",
      "type": "2",
      "position": { "x": 1460, "y": 120 },
      "blocks": [],
      "edges": null,
      "data": {
        "label": "结束",
        "inputs": [
          {
            "name": "input",
            "input": { "type": "string", "blockID": "100001", "name": "description" }
          }
        ]
      }
    },
    {
      "id": "132996",
      "type": "4",
      "position": { "x": 1460, "y": 120 },
      "data": {
        "label": "迭代",
        "inputs": [
          {
            "name": "output2",
            "input": { "type": "list", "blockID": "100001", "name": "file_tree" }
          }
        ],
        "variableParameters": [
          {
            "name": "jj",
            "input": { "type": "string", "blockID": "100001", "name": "description" }
          }
        ],
        "outputs": [
          {
            "name": "student_categories",
            "type": "list",
            "schema": { "type": "array" },
            "required": true,
            "description": "各轮学生分类结果的汇总（每个元素是该轮子块产出的 student_categories 列表）"
          }
        ]
      },
      "blocks": [
        {
          "id": "173594",
          "type": "3",
          "position": { "x": 1460, "y": 120 },
          "blocks": [],
          "edges": null,
          "data": {
            "label": "llm",
            "system": "<role>你是一个教务作业批改辅助系统……</role>",
            "user": "## 用户描述 {{output}} ## 文件树 {{output2}} 请依据以上信息输出规划 JSON。",
            "inputs": [
              {
                "name": "output",
                "input": { "type": "string", "blockID": "132996", "name": "input" }
              }
            ],
            "outputs": [
              {
                "name": "student_categories",
                "type": "list",
                "required": true,
                "description": "根据 file_tree 判断出的学生分类（列表）",
                "schema": {
                  "type": "object",
                  "properties": {
                    "student": { "type": "string", "description": "学生姓名" },
                    "paths": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "该学生对应的文件路径列表"
                    }
                  },
                  "required": ["student", "paths"]
                }
              }
            ]
          }
        }
      ],
      "edges": [
        { "id": "e-132996-173594", "sourceNodeID": "132996", "targetNodeID": "173594" },
        { "id": "e-173594-132996", "sourceNodeID": "173594", "targetNodeID": "132996" }
      ]
    }
  ],
  "edges": [
    { "id": "e-100001-132996", "sourceNodeID": "100001", "targetNodeID": "132996" },
    { "id": "e-132996-900001", "sourceNodeID": "132996", "targetNodeID": "900001" }
  ]
}
```

要点回顾：

- 顶层 `edges` 决定节点之间的流转；迭代体内部连线写在迭代节点的 `edges` 字段里，并以迭代节点自身 `id` 作虚拟入口 / 出口。
- 每条边必填全局唯一的 `id`（示例统一为 `e-{source}-{target}`），顶层边与迭代体内部边共用同一命名空间。
- 子块引用迭代节点时，`blockID` 写迭代节点 `id`，`name` 写迭代体每轮注入的变量名（`index` / `item` / 当前项别名）。
- `{{变量名}}` 中的变量名取 `inputs` 的**本地别名**（示例中 `output` 对应 `input.name = "output"`），不是来源节点上的原始变量名。

---

> 本文件以 `backend/tb-module-workflow/` 的实际源码为事实来源；新增或调整接口、节点类型、定义字段时，须同步修订本文件与 [README.md](./README.md) 的规范清单。
