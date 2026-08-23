# BreenoTTSHook

面向 Android 的 LSPosed 模块，为小布助手接入用户自建的 GPT-SoVITS 服务。

BreenoTTSHook 提供模块 App 与小布内嵌设置入口，使用同一份本地配置完成角色选择、连接检查、试听和语音合成。模块不会修改小布 APK，也不提供默认服务地址或内置凭据。

## 主要功能

- 为小布助手的 TTS 链路提供 GPT-SoVITS 语音合成接入。
- 支持角色与情感目录加载、手动音色、连接检查和试听。
- 支持流式响应，以及 WAV、PCM、MP3、OGG 音频格式。
- 使用 AudioTrack 播放模块生成的音频，并支持播报取消与会话管理。
- 在第三方音频尚未开始播放时，可按配置回退到原始 TTS。
- 模块 App 与小布内嵌设置页共享带版本号的配置。
- 提供中英文设置界面、诊断日志复制和隐私友好的播报标识。

## 工作架构

```text
小布助手
    │
    ▼
LSPosed / YukiHookAPI
    │
    ├── 11.8.3 WebSocket TTS 路由
    └── 12.9.9 Engine / Stream 路由
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

配置由模块的 `ContentProvider` 统一读写，并保存在模块自身的 `SharedPreferences` 中；小布进程只读取该共享配置。

## 兼容性

| 组件 | 支持情况 |
| --- | --- |
| Android | minSdk 31，targetSdk 35 |
| Root 环境 | Magisk 或 KernelSU，配合 LSPosed |
| 小布助手 11.8.3 | WebSocket TTS 路由 |
| 小布助手 12.9.9 | Engine 与流式 TTS 路由 |
| GPT-SoVITS 服务 | `GET /character_list` 与 `POST /tts` |

其他小布版本不会进行模糊匹配。详细边界见 [兼容性说明](docs/COMPATIBILITY.md)。

## 安装与配置

1. 安装模块 APK。
2. 在 LSPosed 中启用模块，并将作用域设置为 `com.heytap.speechassist`。
3. 重启小布助手。
4. 打开 BreenoTTSHook App，或在小布设置中打开“第三方音色”。
5. 填写 GPT-SoVITS 服务地址，加载角色与情感后进行试听。
6. 开启“启用第三方 TTS”。

服务地址由用户自行提供。地址为空时模块不会发起服务请求。

## 配置项

| 分类 | 内容 |
| --- | --- |
| 基础 | 启用状态、服务地址、失败回退 |
| 音色 | 角色、情感、文本语言、手动音色 |
| 合成 | 音频格式、流式响应、语速与生成参数 |
| 网络 | 连接与读取超时 |
| 诊断 | 严格模式、模块播放器、日志等级和试听文本 |

日志等级默认 `ERROR`。诊断记录不包含完整播报文本、Cookie、Token 或完整请求体。

## 从源码构建

构建要求：JDK 17 与 Android SDK 35。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

发布构建可执行：

```powershell
.\gradlew.bat :app:assembleRelease
```

Release APK 需要使用你的发布密钥签名后安装。项目验证脚本为：

```powershell
.\scripts\verify.ps1
```

## 项目结构

```text
app/src/main/java/dev/breenottshook/
├── api/       GPT-SoVITS 客户端、角色目录与诊断记录
├── audio/     音频解码与格式模型
├── config/    共享配置、校验、编码与持久化
├── hook/      LSPosed 注入、版本路由与宿主适配
├── playback/  AudioTrack 播放与流式写入
├── session/   TTS 会话与取消控制
└── ui/        模块 App 与内嵌设置页面
```

## 诊断与反馈

在设置页底部可复制诊断日志。提交问题时请提供设备型号、Android/ColorOS 版本、小布版本、LSPosed 版本、复现步骤和脱敏后的诊断记录。

不要提交真实播报文本、服务地址、API Key、Cookie 或 Token。

更多信息：

- [安装与使用](docs/INSTALL.md)
- [兼容性说明](docs/COMPATIBILITY.md)
- [诊断说明](docs/DIAGNOSTICS.md)
