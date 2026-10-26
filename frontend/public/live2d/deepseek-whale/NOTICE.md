# DeepSeek 鲸鱼娘 Live2D 模型 · 来源与授权

本目录用来放「DeepSeek 鲸鱼娘」的 Live2D 模型文件。

> **这不是 DeepSeek 官方资源。**
> DeepSeek / 深度求索从未发布过任何 Live2D 模型，也没有官方拟人形象，
> 官方视觉标识只有一枚抽象蓝鲸 logo。这里的「鲸鱼娘」是社区二创角色，
> 用于毕业设计、课堂演示等**非商业**场景。

## 模型文件

模型已经在本地（13 个文件，约 3.4 MB），`c_0120.model3.json` 也已修正过，
不需要再跑任何下载脚本。放映页加载的是这个结构：

```
deepseek-whale/
├── c_0120.model3.json      ← 入口
├── c_0120.moc3
├── c_0120.physics3.json
├── c_0120.cdi3.json
├── c_0120.2048/
│   ├── texture_00.png
│   └── texture_01.png
└── motions/
    └── *.motion3.json
```

## 如果要重新获取

从上游仓库手动下载 `assets/model/` 下的同名文件覆盖到本目录即可。
注意上游有三处数据是错的，抓下来必须自己改 `c_0120.model3.json`：

1. `Motions.selfieQuick.File` 写的是 `motions/自拍简短.motion3.json`，
   仓库里实际叫 **`自拍简单`**
2. `Motions.splash.File` 写的是 `motions/吸水.motion3.json`，
   仓库里实际叫 **`喷水`**
3. `Groups` 里 `LipSync` 的 `Ids` 是空数组，要补成 `["ParamMouthOpenY"]`，
   否则说话时嘴不动

模型目录缺文件时，放映页会自动退回 Haru 兜底，不会白屏；但那样显示的不是鲸鱼娘。

## 来源

- 上游仓库：<https://github.com/Andersen216/dsh-whale-girl-live2d>
- 模型子目录：`assets/model/`（模型基名 `c_0120`）

## 版权链（三重，均需署名）

| 权利人 | 贡献 | 主页 |
| --- | --- | --- |
| 上善无形（上善） | 角色形象原作，原创 OC「溟月」 | <https://space.bilibili.com/4456176> |
| ZipZipPipe | 加入 DeepSeek 元素的「女仆鲸鱼娘」二次设计 | <https://space.bilibili.com/4168597> |
| 氵六青 | 本模型（绑定 / 动作 / 表情） | <https://space.bilibili.com/11272072> |

## 许可

美術素材：**CC BY-NC-SA 4.0**（署名 — 非商业性使用 — 相同方式共享）

- 可以：课堂演示、毕设答辩、课程作业等无偿非商业用途，须保留本文件与上述署名。
- 不可以：任何形式的收费、带货、广告变现、售卖周边，或作为付费产品/服务的卖点。
- 二次分发时必须沿用 CC BY-NC-SA 4.0。

运行时：Live2D Cubism Core © Live2D Inc.；pixi.js、pixi-live2d-display 均为 MIT。
