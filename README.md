# BreenoTTSHook

面向 Android 15 / ColorOS 15 的 LSPosed 模块，为小布助手接入用户自己的 GPT-SoVITS 服务。

BreenoTTSHook 不提供默认服务，也不内置服务器地址。用户在模块 APP 或小布助手内嵌设置页填写服务地址后，模块会通过共享配置调用 `GET /character_list` 获取角色和情感，并通过 `POST /tts` 生成语音。

> 项目仍处于开发阶段。不同的小布版本、ColorOS 版本和 LSPosed 环境可能存在兼容差异。遇到问题时，请附上设备系统、小布版本、复现步骤和诊断日志。

## 主要功能

- 使用 LSPosed 注入小布助手 TTS 链路，不修改小布 APK。
- 支持小布助手 11.8.3 的 WebSocket TTS 路径，以及 12.9.9 的兼容性 Hook 路径。
- 接入用户配置的 GPT-SoVITS 服务：`GET /character_list`、`POST /tts`。
- 自动获取角色和情感列表，角色变化时同步更新情感选项。
- 支持流式音频、WAV/PCM/MP3/OGG 格式和模块侧 AudioTrack 播放。
- 第三方音频开始播放前，可按设置回退到小布原 TTS。
- 配置自动保存，模块 APP 与小布内嵌设置页共用同一份带版本号配置。
- 试听、连接检查、地址失焦自动检查，以及 500/502/503/504 失败重试。
- 内嵌设置页支持中英文、原生风格下拉选择、手动音色和高级设置。
- 底部提供诊断日志复制入口，便于提交问题。

## 工作方式

```text
小布助手
    │
    ▼
LSPosed / YukiHookAPI
    │
    ├── 11.8.3 WebSocket TTS
    └── 12.9.9 Engine / Stream Hook
            │
            ▼
       GptSovitsClient
            │
            ├── GET /character_list
            └── POST /tts
                    │
                    ▼
              AudioTrack 播放
```

配置由模块 APP 通过 `ContentProvider` 统一读写，存储在模块自己的 `SharedPreferences` 中。小布进程只读取共享配置，不保存另一份副本。

## 安装与使用

### 环境要求

- Android 15 / ColorOS 15
- 已安装并正常工作的 Magisk 或 KernelSU
- LSPosed
- 小布助手，当前重点验证版本为 11.8.3
- JDK 17（仅源码构建需要）

### 安装模块

1. 安装 Debug APK。
2. 在 LSPosed 中启用 BreenoTTSHook。
3. 作用域只选择小布助手：`com.heytap.speechassist`。
4. 强制停止并重新启动小布助手；必要时重启设备。
5. 打开小布助手设置页中的“第三方音色”，或打开 BreenoTTSHook APP。
6. 填写自己的 GPT-SoVITS 服务地址。
7. 等待自动连接检查和角色列表加载，选择角色与情感后即可试听。
8. 打开“启用第三方 TTS”。配置会自动保存。

地址为空时模块不会发起请求，也不会使用任何内置服务器。填写后的地址会持久化保存，并在两个设置入口之间同步。

## 配置说明

### 基础设置

- 启用第三方 TTS
- GPT-SoVITS 服务地址
- 失败时使用原 TTS

### 音色设置

- 角色
- 情感
- 文本语言
- 手动音色（目录中没有对应角色时使用）

### 高级设置

- 音频格式、流式响应、语速
- `top_k`、`top_p`、`temperature`、`batch_size`
- 连接超时、读取超时
- 严格调试、模块播放器和日志级别

普通用户只需要填写地址并选择音色，其余选项可以保持默认值。

## 诊断与日志

日志默认不记录完整播报文本，只记录必要的定位信息，包括：

- 请求端点、请求次数和 HTTP 状态码
- 角色、情感、字符数和超时配置
- 网络异常类型和有限长度的响应体摘要
- 试听、连接检查和音频播放状态

在设置页底部点击“复制诊断日志”，即可把最近的运行记录复制到剪贴板。日志最多保留 400 条，适合直接附到 Issue 或反馈中。

## 构建

要求 JDK 17 和 Android SDK 35：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目也提供验证脚本：

```powershell
.\scripts\verify.ps1
```

安装、停用和恢复步骤见 [安装说明](docs/INSTALL.md)，兼容性边界见 [兼容性说明](docs/COMPATIBILITY.md)，诊断信息见 [诊断说明](docs/DIAGNOSTICS.md)。

## 安全与隐私

- 源码、APK 和文档不包含默认 API 地址、API Key 或访问令牌。
- API 地址由用户自行填写并保存在本地配置中。
- 使用 HTTP 时，播报文本和音频不会加密传输；建议使用 HTTPS 或可信局域网服务。
- 诊断日志不会记录完整播报文本，也不会记录 Cookie、Token 或完整请求体。
- 停用 LSPosed 作用域即可恢复小布原始 TTS，不需要修改或卸载小布助手。

## 项目结构

```text
app/src/main/java/dev/breenottshook/
├── api/       GPT-SoVITS 客户端、目录缓存和诊断日志
├── config/    共享配置、校验、编码和持久化
├── hook/      LSPosed 注入、版本选择和小布宿主适配
├── session/   TTS 会话、流式分片和取消控制
├── audio/     WAV 解码与音频播放
└── ui/        模块 APP 与小布内嵌设置页
```

## 反馈问题

请在 Issue 中提供：

1. 设备型号、Android/ColorOS 版本；
2. 小布助手版本和 LSPosed 版本；
3. 模块版本、服务端类型和接口格式；
4. 可重复的操作步骤和实际现象；
5. 设置页复制出的诊断日志。

不要提交真实播报文本、API Key、Cookie、Token 或私人服务地址。

## 相关文档

- [安装与使用](docs/INSTALL.md)
- [兼容性说明](docs/COMPATIBILITY.md)
- [诊断说明](docs/DIAGNOSTICS.md)
- [Hook 复用手册](docs/HOOK_REUSE_GUIDE.md)
