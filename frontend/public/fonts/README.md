# 模拟会议自托管字体

本目录只服务于 `views/meeting/` 的视觉层，不改变全站字体策略。

| 文件 | 字体 | 用途 | 许可 |
| --- | --- | --- | --- |
| `space-grotesk-latin*.woff2` | Space Grotesk | 展示标题、巨型数字、英文标签 | SIL Open Font License 1.1 |
| `jetbrains-mono-latin*.woff2` | JetBrains Mono | 会议号、时钟、代码/数据 | SIL Open Font License 1.1 |

- 来源：Google Fonts（`spacegrotesk` v22 / `jetbrainsmono` v24），按拉丁与拉丁扩展两个 `unicode-range` 子集自托管。
- 仅包含 Latin / Latin Extended；中文回退到 `--font-sans` 系统栈。
- Space Grotesk 版权：Florian Karsten。JetBrains Mono 版权：JetBrains s.r.o.。
- 两者均以 SIL OFL 1.1 发布，允许随项目自托管；分发时请保留许可声明。完整许可文本：
  - https://openfontlicense.org/open-font-license-official-text/
  - https://github.com/google/fonts/tree/main/ofl/spacegrotesk
  - https://github.com/google/fonts/tree/main/ofl/jetbrainsmono
- 若字体文件缺失或加载失败，`meeting.css` 会回退到现有 `--font-sans` / `--font-mono`，不影响页面可用性。
