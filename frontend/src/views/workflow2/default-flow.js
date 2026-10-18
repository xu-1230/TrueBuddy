/**
 * 画布默认流程：开始 → 学生名单与批改方案规划 → 教师确认名单与评分标准 → 逐学生批改（迭代）
 * → 生成成绩单与统计 → 结束。
 *
 * 与后端内置定义 `tb-module-workflow/src/main/resources/workflow/standard-grading.json`
 * （工作流名「标准批改作业」，固定 id `1000000000000000001`）**同形**——两边必须同步改，
 * 否则会出现「画布打开看到的一套、实际跑的是另一套」。
 *
 * 结构与后端 `workflow` 定义的 `schema_json` 同构（含迭代节点内嵌的 `blocks` / `edges`，边用
 * `sourceNodeID` / `targetNodeID` 写法），画布页加载时用 `utils/iteration` 的 `expandIteration`
 * 展开成「容器 + 子块 + 内部边」。子块的 `position` 由 `expandIteration` 按迭代体布局重算，
 * 这里写的值只保证定义本身可读。
 *
 * 七条命名约束（由后端实现决定，改动即坏）：
 * 1. **迭代节点的 `outputs` 必须与出口子块**（没有出边的那个子块，这里是 `100008` 输出节点）
 *    的产出**同名**——`IterateNode#aggregate` 按同名把每轮产出聚合成列表，不一致时汇总恒为空。
 * 2. 迭代体内部是一条链：`评分 → 评语 → 输出`，每一环都要把上游的字段**原样透传**下去
 *    （出口子块是唯一被收集产出的那个，它不产出上游字段就等于整轮丢数据）。
 * 3. 迭代体子块引用**迭代节点自身 id**（`100004`）：`item` / `index` 由引擎每轮注入，
 *    `plan` 来自迭代节点的 `variableParameters`。
 * 4. 迭代体**不能引用容器外的节点**，外层变量必须经 `variableParameters` 中转，
 *    且中转的变量名与子块 `input.name` 完全一致。
 * 5. 迭代源取 `inputs` 里**第一个 list 类型**的输入，因此 `student_list` 排在迭代节点 inputs 首位。
 * 6. **人工确认节点的 `inputs` 与 `outputs` 必须同名成对**：`inputs` 决定卡片能回显（进而能回填）
 *    什么，`outputs` 决定要提交什么；前端对同名项做「只读 + 原值回填」（见 docs/12 §4.3）。
 *    这也是「中断点之后不引用中断点之前的节点」这条约束的落地方式。
 * 7. **输出节点（`type: '6'`）只声明 `inputs`**：它的产出名就是这些 `inputs` 的变量名，
 *    执行时把引用到的值原样透传进自己的产出（见 docs/12 §4.4）。它也需要产出，但页面不直接读它 ——
 *    体内那份的产出经迭代聚合后成为「迭代节点的输出列表」，作业批改页面展示的正是那个列表。
 */
export const DEFAULT_FLOW_DEFINITION = {
  nodes: [
    {
      id: '100001',
      type: '1',
      position: { x: 40, y: 280 },
      blocks: [],
      edges: null,
      data: {
        label: '开始',
        outputs: [
          {
            name: 'file_tree',
            type: 'list',
            required: true,
            schema: { type: 'object' },
            description:
              '本次作业提交的文件树（列表）；元素需含 fileId / path / name，fileId 供 read_homework_file_text 读取原文',
          },
          {
            name: 'description',
            type: 'string',
            required: true,
            description: '教师填写的批改要求：满分、评分维度与分值、扣分规则、评语要求都写在这一段里',
          },
          {
            name: 'know',
            type: 'list',
            required: false,
            schema: { type: 'string' },
            description: '知识库引用（占位，本轮不参与提示词）',
          },
        ],
      },
    },
    {
      id: '100002',
      type: '3',
      position: { x: 340, y: 280 },
      blocks: [],
      edges: null,
      data: {
        label: '学生名单与批改方案规划',
        system:
          '<role>你是一名教务作业批改的规划助手。</role>\n<task>把提交的文件树归组成「学生 → 该学生本次提交的全部文件」，并把教师给出的批改要求拆成可执行的批改方案。</task>\n<constraints>\n1. 只依据文件树判断学生归属，不要臆造学生；\n2. 每个文件必须原样保留 fileId，下游只能凭 fileId 读取文件原文；\n3. 同一学生的多份提交（代码 / 报告 / 附件）必须归到同一个学生的 files 里，不要拆成多个学生；\n4. 无法归属到任何学生的文件不要放进结果；\n5. plan 必须写全这几项：评分维度与各维度分值、满分、及格线、扣分要点、等级阈值判定规则；教师要求里没写满分或及格线时，按 100 分制与 60 分及格线处理，并在 plan 里写明这一默认。\n</constraints>',
        user: '## 教师批改要求\n{{description}}\n\n## 提交文件树\n{{file_tree}}\n\n请输出 student_list 与 plan。',
        inputs: [
          {
            name: 'file_tree',
            type: 'list',
            schema: { type: 'object' },
            input: { type: 'list', blockID: '100001', name: 'file_tree' },
          },
          { name: 'description', input: { type: 'string', blockID: '100001', name: 'description' } },
        ],
        outputs: [
          {
            name: 'student_list',
            type: 'list',
            required: true,
            schema: {
              type: 'object',
              properties: {
                student: { type: 'string', description: '学生姓名' },
                files: {
                  type: 'array',
                  items: {
                    type: 'object',
                    properties: {
                      path: { type: 'string', description: '文件路径' },
                      fileId: { type: 'integer', description: '文件 id，用于读取原文' },
                      name: { type: 'string', description: '文件名' },
                    },
                    required: ['path', 'fileId'],
                  },
                  description: '该学生本次提交的全部文件',
                },
              },
              required: ['student', 'files'],
            },
            description: '学生名单：每个元素形如 {student, files:[{path, fileId, name}]}',
          },
          {
            name: 'plan',
            type: 'string',
            required: true,
            description: '批改方案：评分维度与分值、满分、及格线、扣分要点、等级阈值',
          },
        ],
      },
    },
    {
      id: '100003',
      type: '5',
      position: { x: 660, y: 280 },
      blocks: [],
      edges: null,
      data: {
        label: '教师确认名单与评分标准',
        inputs: [
          {
            name: 'student_list',
            type: 'list',
            schema: { type: 'object' },
            input: { type: 'list', blockID: '100002', name: 'student_list' },
          },
          { name: 'plan', input: { type: 'string', blockID: '100002', name: 'plan' } },
        ],
        outputs: [
          {
            name: 'student_list',
            type: 'list',
            required: true,
            schema: { type: 'object' },
            description: '确认后生效的学生名单（结构同规划节点输出）',
          },
          {
            name: 'plan',
            type: 'string',
            required: true,
            description: '确认后生效的批改方案（含满分与及格线）',
          },
        ],
      },
    },
    {
      id: '100004',
      type: '4',
      position: { x: 980, y: 40 },
      data: {
        label: '逐学生批改',
        inputs: [
          {
            name: 'student_list',
            type: 'list',
            schema: { type: 'object' },
            input: { type: 'list', blockID: '100003', name: 'student_list' },
          },
        ],
        variableParameters: [
          { name: 'plan', input: { type: 'string', blockID: '100003', name: 'plan' } },
        ],
        outputs: [
          { name: 'student', type: 'list', required: true, description: '学生姓名：每轮一项，顺序与学生名单一致' },
          { name: 'score', type: 'list', required: true, description: '总分：每轮一项' },
          { name: 'grade', type: 'list', required: true, description: '等级：每轮一项' },
          { name: 'comment', type: 'list', required: true, description: '评语：每轮一项' },
          {
            name: 'deductions',
            type: 'list',
            required: true,
            schema: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  item: { type: 'string', description: '失分项' },
                  diff: { type: 'number', description: '扣分值' },
                  reason: { type: 'string', description: '扣分依据，引用原文片段' },
                },
              },
            },
            description: '扣分明细：每轮一项，元素是该学生的 {item, diff, reason} 列表',
          },
          {
            name: 'readFiles',
            type: 'list',
            required: true,
            schema: { type: 'string' },
            description: '已读文件：每轮一项，元素是该学生已成功读取的文件路径列表',
          },
        ],
      },
      blocks: [
        {
          id: '100005',
          type: '3',
          position: { x: 88, y: 352 },
          blocks: [],
          edges: null,
          data: {
            label: '单份评分',
            system:
              '<role>你是一名严格、公正的作业评分教师。</role>\n<task>读取当前学生的全部提交文件原文，按批改方案给出分数、等级与扣分明细。</task>\n<rules>\n1. 必须对 files 里每一个 fileId 各调用一次 read_homework_file_text，未读完不得评分；\n2. 读取失败的文件按缺失处理：该项计 0 分，并在 deductions 里记一条 reason 说明读取失败；\n3. 每一条扣分必须可追溯到文件原文，reason 里引用关键片段；\n4. score 不超过批改方案里的满分、不低于 0；grade 按批改方案里的等级阈值判定；\n5. student 原样取自当前学生的姓名，不要改写。\n</rules>',
            user: '## 当前学生\n{{item}}\n\n## 批改方案（含评分维度、分值、满分与等级阈值）\n{{plan}}\n\n请先逐个读取该学生的全部文件，再按批改方案里的满分与阈值评分。',
            inputs: [
              { name: 'item', input: { type: 'object', blockID: '100004', name: 'item' } },
              { name: 'plan', input: { type: 'string', blockID: '100004', name: 'plan' } },
            ],
            outputs: [
              { name: 'student', type: 'string', required: true, description: '学生姓名' },
              { name: 'score', type: 'number', required: true, description: '总分' },
              {
                name: 'grade',
                type: 'string',
                required: true,
                description: '等级，按批改方案的阈值判定',
              },
              {
                name: 'deductions',
                type: 'list',
                required: true,
                schema: {
                  type: 'object',
                  properties: {
                    item: { type: 'string', description: '失分项' },
                    diff: { type: 'number', description: '扣分值' },
                    reason: { type: 'string', description: '扣分依据，引用原文片段' },
                  },
                },
                description: '扣分明细',
              },
              {
                name: 'readFiles',
                type: 'list',
                required: true,
                schema: { type: 'string' },
                description: '已成功读取的文件路径清单，用于核对是否漏读',
              },
            ],
          },
        },
        {
          id: '100006',
          type: '3',
          position: { x: 88, y: 536 },
          blocks: [],
          edges: null,
          data: {
            label: '单份评语',
            system:
              '<role>你是一名作业批改教师，负责给学生写评语。</role>\n<task>依据评分结果写出面向学生本人的评语，并把评分结果的其余字段原样透传。</task>\n<rules>\n1. 不得修改 score / grade / deductions / readFiles 的内容，只做透传；\n2. 评语先肯定具体优点，再指出具体问题与改进方向，与扣分明细一一对应；\n3. 评语中不出现其他学生的任何信息；\n4. 只返回 student、score、grade、deductions、readFiles、comment 六个变量，不要把前五个再包进一个对象里。\n</rules>',
            user: '## 当前学生\n{{item}}\n\n## 批改方案\n{{plan}}\n\n## 评分结果\n学生：{{student}}\n总分：{{score}}\n等级：{{grade}}\n扣分明细：{{deductions}}\n已读文件：{{readFiles}}\n\n请输出 student、score、grade、deductions、readFiles、comment。',
            inputs: [
              { name: 'item', input: { type: 'object', blockID: '100004', name: 'item' } },
              { name: 'plan', input: { type: 'string', blockID: '100004', name: 'plan' } },
              { name: 'student', input: { type: 'string', blockID: '100005', name: 'student' } },
              { name: 'score', input: { type: 'number', blockID: '100005', name: 'score' } },
              { name: 'grade', input: { type: 'string', blockID: '100005', name: 'grade' } },
              { name: 'deductions', input: { type: 'list', blockID: '100005', name: 'deductions' } },
              { name: 'readFiles', input: { type: 'list', blockID: '100005', name: 'readFiles' } },
            ],
            outputs: [
              { name: 'student', type: 'string', required: true, description: '学生姓名（透传）' },
              { name: 'score', type: 'number', required: true, description: '总分（透传）' },
              { name: 'grade', type: 'string', required: true, description: '等级（透传）' },
              { name: 'deductions', type: 'list', required: true, description: '扣分明细（透传）' },
              { name: 'readFiles', type: 'list', required: true, description: '已读文件（透传）' },
              { name: 'comment', type: 'string', required: true, description: '面向学生本人的评语' },
            ],
          },
        },
        {
          id: '100008',
          type: '6',
          position: { x: 88, y: 720 },
          blocks: [],
          edges: null,
          data: {
            label: '单份批改结果（输出）',
            inputs: [
              { name: 'student', type: 'string', description: '学生姓名', input: { type: 'string', blockID: '100006', name: 'student' } },
              { name: 'score', type: 'number', description: '总分', input: { type: 'number', blockID: '100006', name: 'score' } },
              { name: 'grade', type: 'string', description: '等级', input: { type: 'string', blockID: '100006', name: 'grade' } },
              { name: 'comment', type: 'string', description: '评语', input: { type: 'string', blockID: '100006', name: 'comment' } },
              { name: 'deductions', type: 'list', description: '扣分明细', input: { type: 'list', blockID: '100006', name: 'deductions' } },
              { name: 'readFiles', type: 'list', description: '已读文件', input: { type: 'list', blockID: '100006', name: 'readFiles' } },
            ],
          },
        },
      ],
      edges: [
        { id: 'e-100004-100005', sourceNodeID: '100004', targetNodeID: '100005' },
        { id: 'e-100005-100006', sourceNodeID: '100005', targetNodeID: '100006' },
        { id: 'e-100006-100008', sourceNodeID: '100006', targetNodeID: '100008' },
        { id: 'e-100008-100004', sourceNodeID: '100008', targetNodeID: '100004' },
      ],
    },
    {
      id: '100007',
      type: '3',
      position: { x: 1540, y: 280 },
      blocks: [],
      edges: null,
      data: {
        label: '生成成绩单与统计',
        system:
          '<role>你是一名教务成绩汇总助手。</role>\n<task>把逐份批改结果汇总成成绩单结构化数据与班级统计。</task>\n<rules>\n1. 输入的 student / score / grade / comment 是四个**等长的平行列表**，**下标相同即同一个学生**，必须按下标对齐，不得错位、不得重排；\n2. 四个列表长度不一致时按最短的长度对齐，并在 summary 里说明「有若干条记录不完整」；\n3. 只使用输入里已有的数据，禁止编造学生、禁止改动分数；\n4. 四个列表都为空时：grade_sheet 返回空数组，三个分数统计返回 0，fail_list 返回空数组，并在 summary 中说明「本次没有产生任何批改记录」；\n5. average_score / highest_score / lowest_score 保留一位小数；\n6. fail_list 列出分数低于批改方案里及格线的学生姓名；没有则返回空数组。\n</rules>',
        user: '## 逐份批改结果（四个等长列表，下标相同即同一个学生）\n学生：{{student}}\n总分：{{score}}\n等级：{{grade}}\n评语：{{comment}}\n\n## 批改方案（含满分与及格线）\n{{plan}}\n\n请输出 grade_sheet、average_score、highest_score、lowest_score、fail_list、summary。',
        inputs: [
          { name: 'student', type: 'list', schema: { type: 'string' }, input: { type: 'list', blockID: '100004', name: 'student' } },
          { name: 'score', type: 'list', schema: { type: 'number' }, input: { type: 'list', blockID: '100004', name: 'score' } },
          { name: 'grade', type: 'list', schema: { type: 'string' }, input: { type: 'list', blockID: '100004', name: 'grade' } },
          { name: 'comment', type: 'list', schema: { type: 'string' }, input: { type: 'list', blockID: '100004', name: 'comment' } },
          { name: 'plan', input: { type: 'string', blockID: '100003', name: 'plan' } },
        ],
        outputs: [
          {
            name: 'grade_sheet',
            type: 'list',
            required: true,
            schema: {
              type: 'object',
              properties: {
                student: { type: 'string', description: '学生姓名' },
                score: { type: 'number', description: '总分' },
                grade: { type: 'string', description: '等级' },
                comment: { type: 'string', description: '评语' },
              },
              required: ['student', 'score', 'grade', 'comment'],
            },
            description: '成绩单结构化数据',
          },
          { name: 'average_score', type: 'number', required: true, description: '班级平均分' },
          { name: 'highest_score', type: 'number', required: true, description: '最高分' },
          { name: 'lowest_score', type: 'number', required: true, description: '最低分' },
          {
            name: 'fail_list',
            type: 'list',
            required: true,
            schema: { type: 'string' },
            description: '不及格学生名单',
          },
          { name: 'summary', type: 'string', required: true, description: '整体讲评' },
        ],
      },
    },
    {
      id: '900001',
      type: '2',
      position: { x: 1900, y: 280 },
      blocks: [],
      edges: null,
      data: {
        label: '结束',
        inputs: [
          { name: 'grade_sheet', input: { type: 'list', blockID: '100007', name: 'grade_sheet' } },
          {
            name: 'average_score',
            input: { type: 'number', blockID: '100007', name: 'average_score' },
          },
          { name: 'fail_list', input: { type: 'list', blockID: '100007', name: 'fail_list' } },
          { name: 'summary', input: { type: 'string', blockID: '100007', name: 'summary' } },
        ],
      },
    },
  ],
  edges: [
    { id: 'e-100001-100002', sourceNodeID: '100001', targetNodeID: '100002' },
    { id: 'e-100002-100003', sourceNodeID: '100002', targetNodeID: '100003' },
    { id: 'e-100003-100004', sourceNodeID: '100003', targetNodeID: '100004' },
    { id: 'e-100004-100007', sourceNodeID: '100004', targetNodeID: '100007' },
    { id: 'e-100007-900001', sourceNodeID: '100007', targetNodeID: '900001' },
  ],
}
