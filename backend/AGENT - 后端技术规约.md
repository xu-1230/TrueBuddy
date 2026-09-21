# AGENT - 后端技术规约

> 项目：TrueBuddy
> 生效日期：2026-06-23
> 上游文档：[总体技术规约](../总体技术规约.md)
> 本规约是后端子项目（`backend/`）的详细规范，开发前需先阅读总体技术规约。

---

## 1. 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.0.1 |
| Java | 17 | — |
| ORM | MyBatis Plus（`mybatis-plus-spring-boot3-starter`） | 3.5.7 |
| 数据库 | PostgreSQL | — |
| AI | Spring AI（`spring-ai-bom` 1.1.0，向量存储 pgvector 2.0.0-M3） | — |
| 安全 | Spring Security（自定义 `TokenAuthenticationFilter`，JWT 暂未引入） | — |
| 工具 | Lombok、Hutool、Guava | 5.8.47 / 33.6.0-jre |

实际依赖以 `tb-dependencies/pom.xml`（版本 BOM）与各模块 `pom.xml` 为准，统一由 `${revision}` 管理版本。

---

## 2. 多模块 Maven 架构（参考芋道 yudao）

```
backend/
├── pom.xml                        # 父 POM（packaging=pom，version=${revision}）
├── tb-dependencies/               # 依赖版本管理 BOM（独立，无 parent）
├── tb-framework/                  # 框架组件
│   ├── tb-common/                 # 通用 pojo（CommonResult / PageResult / ErrorCode 等）
│   ├── tb-spring-boot-starter-web/        # Web 组件（全局异常、CORS，自动装配）
│   ├── tb-spring-boot-starter-mybatis/    # MyBatis Plus 组件（BaseDO / BaseMapperX / 审计填充）
│   └── tb-spring-boot-starter-security/   # 安全组件（Security 配置 / TokenAuthenticationFilter）
├── tb-module-test/                # 测试模块：接口功能连通性验证（单模块）
└── tb-server/                     # 启动模块（BackendApplication + application.yml）
```

> 业务模块（system / infra / ai）当前**未创建**；后续按芋道 master 的**单模块**模式创建（不拆分 api/biz 子模块），`-biz` 前缀仅用于框架 starter 命名（参考芋道 `yudao-spring-boot-starter-biz-*`）。

### 2.1 包结构

包名统一为 `org.example.backend.*`：

```
org.example.backend/
├── framework/                     # 框架组件
│   ├── common/                    # pojo（CommonResult / PageResult）、exception、enums
│   ├── web/                       # 全局异常处理、CORS、自动配置
│   ├── mybatis/                   # BaseDO / BaseMapperX / 分页插件 / 审计填充
│   └── security/                  # Security 配置 / TokenAuthenticationFilter / 工具类
└── module/
    └── test/                      # 测试模块（tb-module-test）
        ├── controller/            # TestController（/test/ping）、AuthController（/system/auth/login）
        ├── service/               # OAuth2TokenService 接口
        │   └── Impl/              # OAuth2TokenServiceImpl
        └── model/vo/              # OAuth2AccessTokenDO / OAuth2AccessTokenPageReqVO
```

### 2.2 分层约定

| 层 | 职责 | 位置 |
|----|------|------|
| Controller | 接收请求、参数校验、返回 `CommonResult<T>` | `xxx/controller/` |
| Service | 业务逻辑、事务控制（接口 + Impl 分离） | `xxx/service/` |
| DO | 数据库实体（继承 `BaseDO`） | `dal/dataobject/` |
| Mapper | 数据访问（继承 `BaseMapperX`，无 XML） | `dal/mysql/` |
| DTO | 接口出入参对象 | `xxx/service/`（单模块内，不再拆分 api 模块） |

> 分层单向依赖：Controller → Service → Mapper，禁止反向依赖。DO 禁止暴露到接口层，接口出入参一律使用 DTO。

---

## 3. 开发规范

| 规范 | 要求 |
|------|------|
| 模块划分 | 业务模块为单模块（如未来创建的 `tb-module-system`），不拆分 api/biz；当前尚未创建，`-biz` 仅用于框架 starter 命名 |
| Controller | `@RestController`，返回统一 `CommonResult` |
| Service | 接口 + Impl 分离，实现类标注 `@Service` |
| DO | 继承 `BaseDO`（含 `@TableId(type = IdType.AUTO) Long id`），类上标注 `@TableName`，审计字段由 `DefaultDBFieldHandler` 自动填充 |
| Mapper | 继承 `BaseMapperX<T>`，提供泛型 CRUD 与字段查询，无 XML |
| 配置 | `application.yml`（位于 `tb-server`），敏感信息通过环境变量注入 |
| SQL | 使用 MyBatis Plus 条件构造器（`Wrappers`），不写手写 SQL |
| 命名 | 类名 PascalCase，方法/变量 camelCase，常量 UPPER_SNAKE |

---

## 4. Controller 规范

- 一律使用 `@RestController`。
- 跨域由全局 CORS 配置统一处理（由安全组件 `SecurityConfig` 开启，无需每个类加 `@CrossOrigin`）。
- 返回类型统一为 `CommonResult<T>`。
- 请求体使用 DTO，禁止直接暴露 DO/Entity 到接口。
- 需免登录的接口（如登录接口）标注 `jakarta.annotation.security.PermitAll`，由安全组件自动放行。

```java
@RestController
@RequestMapping("/example/hello")
public class ExampleController {

    @GetMapping
    public CommonResult<String> hello() {
        return CommonResult.success("ok");
    }
}
```

---

## 5. 统一返回 `CommonResult<T>`

所有接口使用 `CommonResult<T>` 包装（位于 `tb-common` 的 `org.example.backend.framework.common.pojo`）：

```json
// 成功
{ "code": 0, "message": "", "data": <T> }

// 失败
{ "code": 500, "message": "错误描述", "data": null }
```

约定：

- `code = 0` 表示成功，`data` 为业务数据。
- `code != 0` 表示失败，`message` 为前端可直接展示的错误描述。
- 提供静态工厂：`CommonResult.success(data)` / `CommonResult.error(code, message)`。
- 全局异常处理器（`@RestControllerAdvice`，**规划待实现**）将统一捕获异常并转为 `CommonResult`。

---

## 6. 数据库规范

- **PostgreSQL**：`127.0.0.1:5432/tb`
- **数据库脚本**：`sql/` 目录下，`init.sql`（初始化 `uuid-ossp`、`vector` 扩展，只需运行一次）+ RBAC 5 张建表脚本（`system_user` / `system_role` / `system_menu` / `system_user_role` / `system_role_menu`）。
- **MyBatis Plus**：DO 继承 `BaseDO`，Mapper 继承 `BaseMapperX`，使用 `Wrappers` 条件构造器，**无 MyBatis XML**。
- 审计字段（`BaseDO` 的创建/更新人与时间）由 `DefaultDBFieldHandler` 自动填充。
- 分页由 `MybatisPlusAutoConfiguration` 注册的 `PaginationInnerInterceptor(DbType.POSTGRE_SQL)` 统一处理。
- 连接配置放在 `tb-server` 的 `application.yml`，账号密码等敏感信息通过环境变量注入，禁止硬编码。
- 开启下划线转驼峰映射：`mybatis-plus.configuration.map-underscore-to-camel-case: true`。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/tb
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

---

## 7. 接口规范

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/test/ping` | 连通性测试（返回 `pong`） |
| POST | `/system/auth/login` | 登录（`@PermitAll`，返回 `OK` 占位） |

- **上下文路径**：`/api`（端口 8086）

```yaml
server:
  port: 8086
  servlet:
    context-path: /api
```

- 接口路径：`/api/{模块}/{资源}`，遵循 REST 风格。
- 前端 axios 拦截器（**规划**，Axios 暂未引入）将自动解包 `CommonResult`（`code=0` 时透明替换为内层 `data`），后端无需额外包装逻辑。

---

## 8. 安全组件

安全由 `tb-spring-boot-starter-security` 提供，基于 Spring Security，采用无状态 Token 认证（JWT 暂未引入）。

- **自动装配**：`SecurityConfig`（`@AutoConfiguration` + `@EnableWebSecurity` + `@EnableMethodSecurity`）与 `SecurityAutoConfiguration`。
- **认证方式**：无状态 Token（`SessionCreationPolicy.STATELESS`），CSRF 关闭，CORS 开启。
- **Token 解析**：`TokenAuthenticationFilter`（`OncePerRequestFilter`）通过 `SecurityFrameworkUtils.obtainAuthorization` 提取 Token（优先 Header `Authorization`，其次参数 `token`，并剥离 `Bearer ` 前缀），当前为占位实现。
- **免登录配置**：`SecurityProperties`（前缀 `tb.security`）提供 `token-header` / `token-parameter` / `permit-all-urls`；接口可用 `@PermitAll` 或 `permit-all-urls` 枚举放行。
- **兜底规则**：`anyRequest().authenticated()`；异步请求（`DispatcherType.ASYNC`）默认放行以支持 SSE。

```yaml
tb:
  security:
    token-header: Authorization
    token-parameter: token
    permit-all-urls:        # 免登录 URL 列表
      - /test/ping1
```

---

## 9. AI 配置

- AI 模型相关配置（API Key、模型名、超时等）在 `application.yml` 中管理。
- **敏感凭据一律环境变量注入，禁止硬编码**：`application.yml` 中只写 `${VAR}` 占位符，**不提供默认值**（缺失时启动即报错，避免误用弱默认值）。必填变量见下表，模板见项目根目录 `.env.example`。
- 向量检索依赖 PostgreSQL 的 `vector` 扩展（`CREATE EXTENSION vector`）与 `spring-ai-starter-vector-store-pgvector`（版本 2.0.0-M3）。
- 具体业务流程以总体技术规约 3.2「核心业务流程」为准（待生成）。

| 变量 | 必填 | 用途 |
|------|------|------|
| `DB_USERNAME` / `DB_PASSWORD` | 是 | PostgreSQL 账号密码 |
| `OPENAI_API_KEY` | 是 | 对话模型（`spring.ai.openai.api-key`） |
| `OPENAI_EMBEDDING_API_KEY` | 是 | 向量化模型（`spring.ai.openai.embedding.api-key`） |
| `MINIO_SECRET_KEY` | 是 | MinIO 密钥（`minio.secret-key`） |
| `DOCMIND_ACCESS_KEY_ID` / `DOCMIND_ACCESS_KEY_SECRET` | 是 | 阿里云文档智能 AK/SK（知识库文档解析，缺失时启动失败） |
| `OPENAI_BASE_URL` / `OPENAI_CHAT_MODEL` | 否 | 有默认值 |
| `OPENAI_EMBEDDING_BASE_URL` / `OPENAI_EMBEDDING_MODEL` | 否 | 有默认值 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_BUCKET` | 否 | 有默认值 |

- **docx-mcp 子进程**：`tb-module-doctemplate` 的 `mcp-servers-config.json` 不再内联任何凭据，由后端进程环境继承 `MINIO_*`；`docx-mcp/config.yaml` 同样只写 `${VAR}` 占位符。

---

## 10. 启动

**前置：注入环境变量**（`application.yml` 已无明文密钥，缺失必填变量将启动失败）

```bash
cp .env.example .env      # 项目根目录，填入真实凭据（.env 已被 .gitignore 忽略）
set -a; source .env; set +a
```

```bash
cd backend
./mvnw clean install -DskipTests            # 先构建全部模块（含 tb-dependencies BOM）
java -jar tb-server/target/tb-server-0.0.1-SNAPSHOT.jar   # 端口 8086，context-path /api
```

- **Windows 推荐**：在项目根目录执行 `backend\run-dev.cmd` —— 自动读取 `.env`、校验必填变量并启动；加 `-Rebuild` 可先重新构建。其内部调用 `run-dev.ps1`（刻意保持纯 ASCII，避免 Windows PowerShell 5.1 把中文按 GBK 解析导致语法错误）。
- 开发模式可直接使用 IDE 运行 `tb-server` 模块的 `BackendApplication` 主类（需在 Run Configuration 中配置环境变量）。
- 测试：`./mvnw test`。

---

## 11. 与前端对接约定

| 项 | 约定 |
|----|------|
| 端口 / 上下文 | `8086` / `/api` |
| 数据格式 | JSON |
| 统一返回 | `CommonResult<T>`（code=0 成功，code≠0 失败） |
| 跨域 | 全局 CORS（由 `SecurityConfig.cors()` 开启） |
| 实时通信 | SSE（`text/event-stream`）推送（规划） |

---

> **提醒**：后端开发前应先阅读 [总体技术规约](../总体技术规约.md) 与本文档，遵循统一约定。
