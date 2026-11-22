<div align="center">

<img src="docs/icon.png" width="96" alt="Neon" />

# Neon

在手机上替你做事的 AI

<a href="https://github.com/QiGai77/neon/releases/latest"><img src="https://img.shields.io/github/v/release/QiGai77/neon?style=flat-square&label=%E4%B8%8B%E8%BD%BD&color=C6F432&labelColor=111" alt="下载" /></a>
<img src="https://img.shields.io/badge/Android-8.0%2B-111?style=flat-square&logo=android&logoColor=C6F432" alt="Android 8.0+" />
<img src="https://img.shields.io/badge/Kotlin-Compose-111?style=flat-square&logo=kotlin&logoColor=C6F432" alt="Kotlin Compose" />
<img src="https://img.shields.io/github/license/QiGai77/neon?style=flat-square&color=111" alt="MIT" />

[下载 APK](https://github.com/QiGai77/neon/releases/latest) &nbsp;&nbsp; [网页版](https://neon.qigai.tech) &nbsp;&nbsp; [设计规范](docs/design/v0.4/DESIGN.md) &nbsp;&nbsp; [更新记录](https://github.com/QiGai77/neon/releases)

<br />

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/hero-dark.jpg" />
  <img src="docs/hero-light.jpg" alt="Neon 对话 速览 空间 我的，v0.4.4" width="100%" />
</picture>

</div>

<br />

## 四个页面

<table>
<tr>
<td width="50%" valign="top">

### 对话

DeepSeek 官方 API 和 OpenCode Go 订阅，流式输出。思考档位用滑条调，从极速到 Max，思考时顶部的小球会跳、会眨眼。公式按 LaTeX 渲染，Neon 做好的 Word、PDF、表格和单独一行的链接都显示成卡片，点开就能看或分享。图片、文档、视频都能直接上传。几个会话可以同时跑，过程收成一行，点按展开。对话存在本机，连上 Google Drive 后自动备份。

</td>
<td width="50%" valign="top">

### 速览

你关注的 X 账号和按兴趣挑的 GitHub 新项目放在一起看，X 帖子保持原生的样子。每张卡片写明为什么推给你，看不懂的一键问 Neon。也能让 Neon 去 X、Hacker News 和 GitHub 找大多数人还没注意到的消息。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 空间

一页可以自己排的卡片。高铁票、航班、快递、导航、便签这些生活卡片随手加，技能、连接器、沙箱、记忆、定时任务也在这里。进入编辑可以增删、调宽窄、换位置，也可以直接在对话里让 Neon 帮你排。

</td>
<td width="50%" valign="top">

### 我的

给 Neon 起名字、定语气，告诉它你是谁，每次对话前都会带上。动手前可以选每次都问、重要的才问或总是允许，付款、删除、发消息、操作 App 还能单独设成问我。对话可以切换文档和气泡两种样式。额度用量、外观、模型和密钥也在这里。

</td>
</tr>
</table>

## 看看效果

<table>
<tr>
<td width="50%" valign="top">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/features/math-dark.jpg" />
  <img src="docs/features/math-light.jpg" alt="公式和文件卡片" width="100%" />
</picture>

**公式和文件卡片**
数学公式直接排版。Neon 做好的 Word、PDF 和引用的链接都是卡片，点开就看，也能分享出去。

</td>
<td width="50%" valign="top">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/features/think-dark.jpg" />
  <img src="docs/features/think-light.jpg" alt="思考滑条" width="100%" />
</picture>

**想多久由你定**
从极速到 Max 一条滑条。图片、文档、视频拖进来就能让 Neon 处理。

</td>
</tr>
<tr>
<td width="50%" valign="top">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/features/space-dark.jpg" />
  <img src="docs/features/space-light.jpg" alt="空间编辑" width="100%" />
</picture>

**空间自己排**
高铁票、航班、快递、导航都能做成卡片。编辑时卡片会轻轻晃动，可以换位置、调宽窄、删掉。

</td>
<td width="50%" valign="top">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/features/x-dark.jpg" />
  <img src="docs/features/x-light.jpg" alt="速览里的 X 帖子" width="100%" />
</picture>

**原汁原味的 X**
头像、认证标、回复转帖喜欢都在原来的位置，多了一个问 Neon。

</td>
</tr>
</table>

<sub>截图由 Roborazzi 用示例数据渲染。</sub>

## 能做什么

**Linux 沙箱**
内置 Alpine Linux，带 Python 和常用开发工具，不联网也能跑。Neon 可以在里面写脚本、处理表格、跑程序。删除文件这类命令会先停下来问你。

**Hands**
让 Neon 直接操作手机上的应用，比如打开菜鸟查快递。支持无障碍和 Shizuku 两种方式。遇到付款、发送、删除，先指纹确认；遇到密码框，交给你自己输入。

**连接器**
内置 35 个常用服务。Google 登录一次就能读写 Gmail、日历和 Drive，对话也顺带备份到 Drive，只需要在 Google Cloud 建一个自己的桌面应用客户端。速览里的 X 帖子不用登录，加上想看的账号就行。远程 MCP 服务器自动走 OAuth 授权，也可以在沙箱里跑本地 MCP。Webhook 可以接 Tasker 等自动化工具。

**安全表单和内置浏览器**
需要登录网站时，Neon 会弹出安全表单请你填账号密码或粘贴 Cookie，加密存在本机，Neon 只拿到一个编号，看不到具体的值。打开网页时它用编号注入 Cookie 或自动填好登录框。你也可以在内置浏览器里自己登录，一键把登录状态存成安全表单。

**技能**
技能是一个 SKILL.md 文件，自带 12 个，也能从技能市场安装。对话里输入 `/` 调用。Hands 跑完一次任务，可以把过程存成技能，下次直接用。

**记忆和定时任务**
Neon 会记住你告诉它的事，每次对话前带上。定时任务按时在后台运行，跑完发通知。

<br />

## 设计

v0.4 换成了液态玻璃：浮动的磨砂导航，半透明卡片，背后是柔和的色场。颜色、模糊、圆角、间距、动效参数和每个交互的说明都在 [DESIGN.md](docs/design/v0.4/DESIGN.md)，26 张设计稿在 [screens](docs/design/v0.4/screens)。

## 安装

1. 在 [Releases](https://github.com/QiGai77/neon/releases/latest) 下载最新的 `neon-v*.apk`
2. 允许安装未知来源应用
3. 打开后连接 DeepSeek 或导入 OpenCode Go 的 Key

从 v0.3.1 起可以直接覆盖升级。装过 v0.2 或 v0.3.0 的话，签名不同，需要先卸载旧版。

要用 Hands，到系统设置里开启 Neon 的无障碍服务，或者安装 Shizuku。

## 隐私

Neon 没有自己的服务器。API Key 和安全表单加密存在本机 Keystore。Google 授权只申请邮件、日历和 Drive 需要的范围，Drive 里只能改 Neon 自己创建的文件，其他文件只读。

## 构建

```bash
cd android
python3 scripts/prepare-offline-sandbox.py   # 下载 Alpine 和 PRoot
gradle :app:assembleDebug
```

沙箱里要带 Python，用 `scripts/build-offline-rootfs.sh` 构建 rootfs，需要 Docker。

推送 `v*` 标签时，GitHub Actions 会构建签名 APK 并上传到对应的 Release。

## 网页版

网页版代码在仓库根目录，`pnpm install && pnpm dev` 启动，需要 Node.js 22.12 以上。

<br />

<div align="center">
<img src="docs/icon.png" width="40" alt="" />
<br />
<sub>MIT License</sub>
</div>
