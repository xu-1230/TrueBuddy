# PPT 设置收敛为登录态 + 服务端用户偏好

> 2026-11-18 ｜ 相关：[docs/specs/2026-11-18-数字人与音色设置.md](../specs/2026-11-18-数字人与音色设置.md)、[docs/doc-lecture.md](../doc-lecture.md) §5.7、[docs/doc-api.md](../doc-api.md)、[docs/doc-database.md](../doc-database.md)

## 背景

`/index/ppt` 原来的数字人形象只能在面板底部一个胶囊按钮里按登记表顺序轮换，音色则固定为 `tb.lecture.digital-human.tts-voice` 一个全局配置。本轮要把入口改成顶栏「设置」弹窗：数字人 Tab 选择展示的形象，声音 Tab 选择内置音色或复刻音色。由此产生三个必须先定死的问题：

1. 「当前选了谁」放在哪里——浏览器本地、全局默认、还是按登录用户存服务端；
2. `/ppt/**` 一直是整段免登录，但音色库需要按 `creator` 隔离、复刻需要归属与删除权限；
3. 形象目录要不要把 Live2D 模型地址也搬进数据库。

## 决策

1. **只有 `POST /ppt/speech/speak` 保留在免登录白名单**：放映页刷新 / accessToken 过期时仍能出声。`/ppt/speech/optimize`、`/ppt/speech/narrate`、`/ppt/avatar/**`、`/ppt/voice/**`、`/ppt/preference` 全部要求登录（`/index` 本身已要求登录）。
2. **当前选择按登录用户存服务端**：新增 `lecture_avatar_preference`（`creator` 唯一，存 `avatar_model_code` 与 `voice_code`），它是「当前使用中的形象 / 音色」的唯一真相源；PPT 页挂载时先读它再初始化 Live2D，浏览器本地不再保存选择。
3. **形象目录与加载配置分离**：`avatar_model` 只存可展示清单（编码 / 名称 / 类型 / 缩略图 / 排序 / 启停）；模型地址、兜底顺序与取景参数仍是前端 `views/ppt/configs/avatar.js` 的静态事实。数据库不能写入任意模型 URL。
4. **音色库分内置与复刻**：`avatar_voice` 中 `creator` 为空的是内置音色（全员可见），复刻音色 `creator` 为创建者（仅本人可见、可删）。复刻走 DashScope `VoiceEnrollmentService.createVoice`，音色编码为厂商返回的 `voice_id`。
5. **复刻样本的输入边界**：DashScope 只接受公网音频 URL。粘贴公网 URL 始终可用；上传文件仅在配置 `tb.lecture.digital-human.public-base-url`（公网可达、允许匿名读取的存储）时启用。未配置时设置弹窗禁用上传，不假装 localhost 能被阿里云访问。
6. **音色失效回退默认**：播报请求带 `voiceCode`；音色不存在 / 无权限 / 解析失败时回退全局默认音色，SSE `status started` 标记 `voiceFallback`；前端提示一次并把失效偏好回退默认。复刻音色在公开的 speak 接口上没有登录态时同样回退。

## 为什么不是别的

- **只存 localStorage**：刷新同浏览器能保留，但换设备 / 换登录用户就丢；更关键的是复刻音色在服务端有归属，而「当前选择」没有归属，会出现「列表里看不到、偏好却指向它」的悬空状态。
- **表内 `is_default` 全局默认**：只建两张表、实现最短，但所有老师共享一套选择，在多人使用时会互相覆盖；与仓库既有「按 creator 隔离」的惯例冲突。
- **`/ppt/**` 继续整段公开**：复刻是付费且占厂商音色额度，匿名即可创建 / 删除无法接受；偏好也需要登录用户才能落库。只保留 speak 公开，是对「放映现场必须能出声」与「写操作必须可归属」的折中。
- **形象目录完全数据库驱动**：新增形象不用改代码，但要把任意 URL / framing JSON 写进库，需要额外的 URL 校验、跨域与回退策略；现有模型是随仓库分发的静态资源，放代码里更可控。
- **卡片内嵌实时 Live2D 预览**：9 个模型同时挂 WebGL 上下文，大肥鱼约 30MB，弹窗会被拖死；改为离线静态截图（`frontend/scripts/generate-avatar-previews.mjs`），真实效果仍在右侧主面板看。

## 后果

- `/ppt/speech/optimize`、`/ppt/speech/narrate` 从匿名可用变为要求登录；`frontend/scripts/smoke-narrate.mjs` 需要 `TB_TOKEN`。
- `tb-module-lecture` 新增对 `tb-spring-boot-starter-security` 与 `tb-module-infra` 的依赖（登录用户上下文 + 音色样本存储）。
- 三张新表必须先在 `tb` 库执行 `backend/sql/avatar_model.sql`、`avatar_voice.sql`、`lecture_avatar_preference.sql`；未执行时目录 / 偏好接口报错，但 speak 会回退默认音色继续出声。
- 37 条内置音色的官方试听地址来自阿里云文档页，属于外部资源；失效时只是试听失败，不影响选择与播报。
- 上传复刻依赖部署侧把 MinIO 暴露到公网并允许匿名读取；本机默认 `http://localhost` 下只能用 URL 复刻。管理端 `views/admin/ppt/AvatarConfigView.vue` 仍是演示 mock，不在本决策范围内。
