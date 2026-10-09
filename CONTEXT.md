# CONTEXT — 领域术语表

> 纯术语表。实现细节见 [docs/16-PPT演讲与模拟会议方案.md](docs/16-PPT演讲与模拟会议方案.md)，不可逆决策见 [docs/adr/](docs/adr/)。

## 模拟会议

| 术语 | 定义 | 不是什么 |
| --- | --- | --- |
| **会议**（meeting） | 老师一次建会产生的共享会话，生命周期只有「进行中 → 已结束」两态；没有独立的"开始"步骤，`create` 即进行中 | 不是预约、不是排期 |
| **会议号**（`meetingNo`） | 6 位随机数，同时是入会链接的路径段 `/meeting/{meetingNo}` | 不是内部主键 `meetingId` |
| **主持人凭据**（`hostToken`） | 建会时生成、**只在 `create` 响应里返回一次**的老师侧凭据；丢失即永久失去该会的主持能力 | 不是登录态；不可重新签发（见 ADR-0003） |
| **参会凭据**（`attendeeToken`） | 每个学生入会时签发一次，用于状态查询与消息落库 | 不是房间令牌（那是 LiveKit JWT） |
| **在线成员** | **只有一义：LiveKit 房间当前的参与者**（前端状态名 `roomAttendees`）。由 `RoomEvent.ParticipantConnected/Disconnected` 得出 | ⚠️ 不是数据库字段，不由 `lecture_meeting_attendee` 计算（见 ADR-0002）；房间连不上时前端改标「入会记录」，也不再给出在线数字 |
| **累计入会名单** | `lecture_meeting_attendee` 的全表语义（前端状态名 `joinLedger`）：凡入会过者各有一行，带 `joinTime` / `leaveTime` | 不是"当前在线名单" |
| **尚未显式离开** | `leaveTime IS NULL` 的行状态。写入方只有两个：`POST /meeting/leave` 与会议结束批量补记 | 不等于"此刻在线"——崩溃/断网/关标签页，以及**刷新页面**都不发 leave。刷新后 sessionStorage 里的凭据继续用、不会重新 join，此时写 leave 等于记下一条"已离开但其实还在"的假事实，比留着更糟（这是设计而非遗漏） |
| **会议结束**（close） | 老师主动动作：`status=2` + 全部行补 `leaveTime` + 删 LiveKit 房间 | 不是超时自动结束（当前无此机制） |
| **老师 / 学生** | `role=1` 只出现在**消息**上（老师发言）；参会表只存学生，老师不进参会表（docs/16 Q22：老师是控制台，不参会） | 参会表的 `role` 列恒为 2，不是废弃字段 |

## PPT 演讲

| 术语 | 定义 |
| --- | --- |
| **讲稿** | 幻灯片的 `remark`（备注）。**不落库**，只存在于前端 PPTist JSON 里；AI 优化的写回目标就是它 |
| **本场时长** | 老师输入的分钟数，按 `chars-per-second`（4.0 字/秒）均摊成每页目标字数。换算只发生在后端一处 |
| **失败页**（`failedIndexes`） | 整篇生成中未拿到有效结果的页码。部分失败**不是错误**，前端可只重试这些页 |
