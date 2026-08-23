# BreenoTTSHook

这个 LSPosed 模块可以为ColorOS的小布助手接入用户自建的 GPT-SoVITS 服务，从而实现自定义小布音色。

目前仅测试了小布助手 12.9.9版本，可以正常使用，其它版本正逐渐考虑适配。如果希望兼容其它API提供商，欢迎提issue。

## 主要功能

- 替换小布助手的 TTS 链路，提供 GPT-SoVITS 语音合成接入。

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
| 小布助手 12.9.9 | Engine 与流式 TTS 路由 |
| GPT-SoVITS 服务 | `GET /character_list` 与 `POST /tts` |

其他小布版本不会进行模糊匹配。详细边界见 [兼容性说明](docs/COMPATIBILITY.md)。

## 安装与配置

1. 安装模块 APK。
2. 在 LSPosed / Vector 中启用模块，并将作用域设置为 `com.heytap.speechassist`。
3. 重启小布助手进程。
4. 在小布设置中打开多出来的“第三方音色”选项卡。
5. 填写TTS服务地址，开启第三方TTS，试听使用。

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

更多信息：

- [安装与使用](docs/INSTALL.md)
- [兼容性说明](docs/COMPATIBILITY.md)
- [诊断说明](docs/DIAGNOSTICS.md)
