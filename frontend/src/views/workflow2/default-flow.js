/**
 * 画布默认流程：开始 → 大模型（学生名单规划）→ 迭代（迭代体内一个大模型逐份批改）→ 结束。
 *
 * 结构与后端 `workflow` 定义的 `schema_json` 同构（含迭代节点内嵌的 `blocks` / `edges`，边用
 * `sourceNodeID` / `targetNodeID` 写法），画布页加载时用 `utils/iteration` 的 `expandIteration`
 * 展开成「容器 + 子块 + 内部边」。变量绑定与后端 `resources/text.json` 的场景保持一致，便于对照。
 *
 * 两条命名约束（由后端实现决定）：
 * 1. 迭代节点的 `outputs` 变量名要与迭代体子块的产出变量名一致（此处都是 `result`）——
 *    `IterateNode.aggregate` 按同名把每轮子块的产出聚合成列表。
 * 2. 迭代体子块引用迭代节点自身 id（`132996`）：`item` 是每轮注入的当前项，
 *    `jj` 来自迭代节点的 `variableParameters`。
 */
export const DEFAULT_FLOW_DEFINITION = {
  nodes: [
    {
      id: '100001',
      type: '1',
      position: { x: 40, y: 240 },
      data: {
        label: '开始',
        outputs: [
          {
            name: 'file_tree',
            type: 'list',
            required: true,
            schema: { type: 'object' },
            description: '文件树（列表）',
          },
          { name: 'description', type: 'string', required: true, description: 'AI 批改说明' },
          {
            name: 'know',
            type: 'list',
            required: true,
            schema: { type: 'string' },
            description: 'AI 知识（列表）',
          },
        ],
      },
    },
    {
      id: '100002',
      type: '3',
      position: { x: 400, y: 180 },
      data: {
        label: '学生名单规划',
        system: '<role>你是一个教务作业批改辅助系统，从文件树中判断出需要批改与登记成绩的学生名单。</role>',
        user: '## 用户描述\n{{description}}\n\n## 文件树\n{{file_tree}}\n\n请依据以上信息输出规划 JSON。',
        inputs: [
          { name: 'description', input: { type: 'string', blockID: '100001', name: 'description' } },
          {
            name: 'file_tree',
            type: 'list',
            schema: { type: 'object' },
            input: { type: 'list', blockID: '100001', name: 'file_tree' },
          },
        ],
        outputs: [
          {
            name: 'student_categories',
            type: 'list',
            required: true,
            schema: {
              type: 'object',
              properties: {
                student: { type: 'string', description: '学生姓名' },
                paths: {
                  type: 'array',
                  items: { type: 'string' },
                  description: '该学生对应的文件路径列表',
                },
              },
              required: ['student', 'paths'],
            },
            description:
              '根据 file_tree 判断出的学生分类（列表），每个元素形如 {student: 学生姓名, paths: [该学生对应的文件路径列表]}',
          },
        ],
      },
    },
    {
      id: '132996',
      type: '4',
      position: { x: 820, y: 60 },
      data: {
        label: '逐份批改',
        inputs: [
          {
            name: 'student_categories',
            type: 'list',
            schema: { type: 'object' },
            input: { type: 'list', blockID: '100002', name: 'student_categories' },
          },
        ],
        variableParameters: [
          { name: 'jj', input: { type: 'string', blockID: '100001', name: 'description' } },
        ],
        outputs: [
          {
            name: 'result',
            type: 'list',
            required: true,
            schema: { type: 'string' },
            description: '逐份批改结果汇总（每轮子块 result 的聚合）',
          },
        ],
      },
      blocks: [
        {
          id: '173594',
          type: '3',
          position: { x: 24, y: 64 },
          blocks: [],
          edges: null,
          data: {
            label: '单份批改',
            system: '<role>你是一个作业批改助手，请根据批改要求批改这一份作业。</role>',
            user: '## 批改要求\n{{jj}}\n\n## 当前学生\n{{item}}\n\n请输出该学生的批改结果。',
            inputs: [
              { name: 'item', input: { type: 'object', blockID: '132996', name: 'item' } },
              { name: 'jj', input: { type: 'string', blockID: '132996', name: 'jj' } },
            ],
            outputs: [
              { name: 'result', type: 'string', required: true, description: '单份作业的批改结果' },
            ],
          },
        },
      ],
      edges: [
        { id: 'e-132996-173594', sourceNodeID: '132996', targetNodeID: '173594' },
        { id: 'e-173594-132996', sourceNodeID: '173594', targetNodeID: '132996' },
      ],
    },
    {
      id: '900001',
      type: '2',
      position: { x: 1320, y: 240 },
      data: {
        label: '结束',
        inputs: [
          { name: 'input', input: { type: 'list', blockID: '132996', name: 'result' } },
        ],
      },
    },
  ],
  edges: [
    { id: 'e-100001-100002', sourceNodeID: '100001', targetNodeID: '100002' },
    { id: 'e-100002-132996', sourceNodeID: '100002', targetNodeID: '132996' },
    { id: 'e-132996-900001', sourceNodeID: '132996', targetNodeID: '900001' },
  ],
}
