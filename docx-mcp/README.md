# docx-mcp

一个 docx 相关的 MCP 服务，提供**两层能力**：

1. **渲染往返**：`docx ⇄ docx-wrapper HTML`，用于整篇改写与格式保真还原；
2. **文档理解与定位编辑**：直接检索、按位置读、按位置改、比对、增删段落，
   补上「不能搜、不能按位置读、不能按位置改、不能 diff、不能增删段落」的缺口。

不依赖任何外部脚本或临时文件落盘：`docx_open` 返回会话句柄 `docId`，
`docx_to_html` 可返回 `html_handle`，后续调用只传句柄即可。

## 一、工具清单

### 渲染往返（原有能力，保真度关键在 `template`）

| 工具 | 作用 |
| --- | --- |
| `docx_to_html` | docx → docx-wrapper HTML。`output_path` 落盘 / `as_handle` 返回进程内句柄 |
| `html_to_docx` | HTML → docx（**务必传 `template`**，继承 styles.xml / 页面设置 / numbering.xml） |

### 文档理解

| 工具 | 作用 |
| --- | --- |
| `docx_open` | 打开文档 → `docId` + 结构统计 / 页面设置 / 疑似标题 / 封面字段 / 表格概览；文件托管到 MCP 缓存目录 |
| `docx_search` | 文档内检索，命中自带 `part / paragraphIndex / tableIndex / row / col / charOffset` + 上下文 |
| `docx_get_text` | 带稳定索引的纯文本导出（支持分页、表格网格），取代「整份 HTML 落盘再剥标签」 |
| `docx_get_runs` | 段落 run 边界与格式；明确 `matchAcrossRuns=true` |
| `docx_get_tables` | 表格行列数、合并单元格、单元格路径 → 文本 |

### 定位编辑（只改 `w:t`，不动 `w:pPr` / `w:rPr`）

| 工具 | 作用 |
| --- | --- |
| `docx_replace` | 带 `scope` 的批量替换 + `dryRun` + 结构化变更报告；自动跨 run；幂等 |
| `docx_set_cell` | 写入单元格（保留其原有格式，支持 `\n` 多段） |
| `docx_insert_paragraph` | 插入段落，可继承参考段落格式 |
| `docx_delete_paragraph` | 删除段落 |
| `docx_fill_fields` | 填充封面/表单字段：`{{占位符}}` 或「标签：」后空值 |

### 校验

| 工具 | 作用 |
| --- | --- |
| `docx_diff` | 文本级 / 格式级比对，证明「只改了文字、版式零变化」 |
| `docx_validate` | 占位符残留、空单元格、关键词计数、页面设置完整性体检 |

## 二、寻址约定

```
p:12                正文第 12 个段落（0 起，按文档顺序）
t:3                 第 3 个表格（含嵌套表格，按文档顺序全局编号）
t:3/r:2/c:1         表格 t:3 中逻辑网格第 2 行第 1 列的单元格
t:3/r:2/c:1/p:0     该单元格内的第 0 个段落
```

`r` / `c` 是**逻辑网格**坐标：合并单元格以左上角参与寻址，被 `rowspan` / `colspan`
覆盖的延续格不单独出现。

`docx_replace` 的 `scope` 支持：

```jsonc
{"paragraph": 151}                  // 只在该段号内替换
{"location": "p:151"}               // 同上，直接给路径
{"table": 3, "row": 2, "cell": 1}   // 只在该单元格内替换
{"part": "body" | "table"}          // 只改正文 / 只改表格
{"after": "难点："}                  // 只改该锚点之后的那一处
{"before": "备注"}                   // 只改该锚点之前的那一处
{"occurrence": 2}                   // 只改全局第 2 次出现
```

## 三、理想执行路径

```
1  docx_open(src)                                  → docId + 表格结构 + 封面字段 + 章节骨架
2  docx_search(docId, "第3章|SQL|数据库")           → 命中位置清单（含 table/row/col）
3  docx_get_text(docId, page=1..n)                 → 按段号逐段改写，不再依赖 HTML 剥标签
4  docx_replace(docId, rules, dry_run=true)        → 先看每条规则命中位置，确认无歧义
5  docx_replace(docId, rules)                      → 带 scope 的规则各写各的，不再妥协统一文案
6  docx_diff(src, dst, level="format")             → 断言版式零变化
7  docx_fill_fields(dst, {...})                    → 填封面空字段
```

全程 6~7 次 MCP 调用、零外部脚本、零临时文件，也不需要把语义不同的重复文本强行统一。

## 四、两个已修复的语义缺陷

1. **`new` 包含 `old` 导致文本翻倍**
   旧实现先做整段匹配改写，再无条件跑一遍文本节点级字面替换，
   于是刚写进去的 `new` 又被替换一次。
   现在整段命中后即跳过文本节点级替换，并在报告中给出
   `new 包含 old，已强制 single 模式` 的告警。

2. **长短串互相吞噬靠人脑排序**
   新增 `order="longest-first"`（默认）按 `old` 长度降序执行，
   并检测「A 是 B 的子串」给出顺序提示。

## 五、运行

```bash
uv sync
uv run python main.py
```

### 后端拉起方式（mcp-servers-config.json）

Spring AI 后端（`tb-module-doctemplate/src/main/resources/mcp-servers-config.json`）
**直接用 `.venv\Scripts\python.exe` 绝对路径启动 `main.py`**，不再经由 `uv run`：

- 不依赖 `uv` 在启动方 PATH 中（IDEA/服务/计划任务等环境常缺 `D:\Python\Scripts`，
  这是「终端能起、IDE 里 MCP 启动失败」的典型根因）；
- 每次启动不再触发 uv 的隐式依赖同步，启动更快且无网络依赖。

**前置条件**：`.venv` 必须已就绪——克隆或改动 `pyproject.toml` / `uv.lock` 后先在
`docx-mcp/` 下执行一次 `uv sync`。若 `.venv` 缺失或损坏，删除后重新 `uv sync` 即可。

> 备选方案：改回 `"command": "uv", "args": ["run", "--no-sync", "--directory", ...]`
> 也可以，但仍要求 `uv.exe`（`D:\Python\Scripts`）对后端进程可见，稳定性不如直连 venv 解释器。

### MinIO 配置（凭据一律环境变量注入）

`docx_to_html` 的 `image_mode="url"` 需要 MinIO；配置读取顺序为
**环境变量 > 项目根目录 `config.yaml` 的 `minio` 段**。`config.yaml` 中只写
`${VAR}` / `${VAR:默认值}` 占位符，**不落明文凭据**：

| 环境变量 | 必填 | 说明 |
| --- | --- | --- |
| `MINIO_SECRET_KEY` | 是 | 密钥，无默认值 |
| `MINIO_ENDPOINT` | 否 | 默认 `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | 否 | 默认 `admin` |
| `MINIO_BUCKET` | 否 | 默认 `test-bucket` |
| `MINIO_PUBLIC_BASE_URL` / `MINIO_PRESIGN_EXPIRES` | 否 | 对外基址 / 预签名有效期 |

被后端以 stdio 方式拉起时，这些变量由后端进程环境继承（见
`tb-module-doctemplate/src/main/resources/mcp-servers-config.json`）。

凭据不全或上传失败时**不再静默**：会在 stderr 打印缺失的变量名，并把图片降级为占位 `<img>`。
