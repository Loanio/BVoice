<div align="right">

[English](README.en.md) | **中文**

</div>

```
██████╗  ██╗   ██╗ ██████╗ ██╗  ██████╗ ███████╗
██╔══██╗ ██║   ██║██╔═══██╗██║ ██╔════╝ ██╔════╝
██████╔╝ ╚██╗ ██╔╝██║   ██║██║ ██║      █████╗
██╔══██╗  ╚████╔╝ ██║   ██║██║ ██║      ██╔══╝
██████╔╝   ╚██╔╝  ╚██████╔╝██║ ╚██████╗ ███████╗
╚═════╝     ╚═╝    ╚═════╝ ╚═╝  ╚═════╝ ╚══════╝
```

<p align="center">
  <a href="https://github.com/Loanio/BVoice"><img src="https://img.shields.io/github/stars/Loanio/BVoice?label=stars" alt="stars"/></a>
  <a href="https://github.com/Loanio/BVoice/releases/latest"><img src="https://img.shields.io/github/v/release/Loanio/BVoice?include_prereleases&label=release" alt="release"/></a>
  <a href="https://github.com/Loanio/BVoice/releases/latest"><img src="https://img.shields.io/github/downloads/Loanio/BVoice/total?label=downloads" alt="downloads"/></a>
</p>

<p align="center">
  Android · LSPosed · YukiHookAPI · GPT-SoVITS · 小布助手
</p>

# 小布音色替换

面向 Android 的 LSPosed 模块，为小布助手接入用户自建的 GPT-SoVITS 服务，从而实现自定义小布音色。

BVoice 提供独立模块 App 与小布内嵌设置入口。两者共享同一份本地配置，可用于角色选择、连接检查、试听和语音合成。

## 界面预览

| 小布设置页面 | 音色设置页面 |
| --- | --- |
| ![小布设置页面](images/小布设置页面.jpg) | ![音色设置页面](images/音色设置页面.jpg) |

## 后端服务

本模块可以使用 [Uni-TTS](https://github.com/X-T-E-R/Uni-TTS) 作为 TTS 后端服务。接口详情请参阅 [Uni-TTS API 文档](https://www.yuque.com/xter/zibxlp/kkicvpiogcou5lgp)。

> [!IMPORTANT]
> **Beta 软件。** 功能、兼容性和稳定性仍在持续验证中，后续可能发生变化。目前仅支持[兼容性](#兼容性)中列出的小布助手版本。

## 目录

- [界面预览](#界面预览)
- [后端服务](#后端服务)
- [主要功能](#主要功能)
- [工作架构](#工作架构)
- [兼容性](#兼容性)
- [下载](#下载)
- [安装与配置](#安装与配置)
- [配置项](#配置项)
- [从源码构建](#从源码构建)
- [项目结构](#项目结构)
- [诊断与反馈](#诊断与反馈)

## 主要功能

- 为小布助手的 TTS 链路提供 GPT-SoVITS 语音合成接入。
- 支持角色与情感目录加载、手动音色、连接检查和试听。
- 支持流式响应，以及 WAV、PCM、MP3 和 OGG 音频格式。
- 使用 `AudioTrack` 播放模块生成的音频，并支持播报取消与会话管理。
- 开启配置后，在第三方音频开始播放前回退到小布原始 TTS。
- 模块 App 与小布内嵌设置页共享带版本号的配置。
- 提供中英文设置界面、可复制的诊断日志和隐私友好的播报标识。

## 工作架构

```text
小布助手
    |
    v
LSPosed / YukiHookAPI
    |
    +-- 12.9.9 Engine / 流式 TTS 路由
            |
            v
      GptSovitsClient
            |
            +-- GET /character_list
            +-- POST /tts
                    |
                    v
              AudioTrack 播放
```

配置通过模块的 `ContentProvider` 读写，并存储在模块自己的 `SharedPreferences` 中。小布进程只读取这份共享配置。

## 兼容性

| 组件 | 支持情况 |
| --- | --- |
| Android | minSdk 31，targetSdk 35 |
| Root 环境 | Magisk 或 KernelSU，配合 LSPosed |
| 小布助手 12.9.9 | Engine 与流式 TTS 路由 |
| GPT-SoVITS 服务 | `GET /character_list` 与 `POST /tts` |

## 下载

- [BVoice Debug APK](https://github.com/Loanio/BVoice/releases/download/v0.1.0/BVoice-0.1.0-debug.apk) —— 使用 Android debug 密钥签名。
- [BVoice Release APK](https://github.com/Loanio/BVoice/releases/download/v0.1.0/BVoice-0.1.0-release-signed.apk) —— 使用正式 release 密钥签名。

## 安装与配置

1. 安装模块 APK。
2. 在 LSPosed 中启用模块，并将作用域设置为 `com.heytap.speechassist`。
3. 重启小布助手。
4. 打开 BVoice App，或在小布设置中打开“第三方音色”。
5. 填写 GPT-SoVITS 服务地址，加载角色与情感目录，然后进行试听。
6. 开启“启用第三方 TTS”。

## 配置项

| 分类 | 可用配置 |
| --- | --- |
| 基础 | 启用状态、服务地址、失败回退 |
| 音色 | 角色、情感、文本语言、手动音色 |
| 合成 | 音频格式、流式响应、语速和生成参数 |
| 网络 | 连接超时和读取超时 |
| 诊断 | 严格模式、模块播放器、日志级别和试听文本 |

## 从源码构建

环境要求：JDK 17 和 Android SDK 35。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 输出到 [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)。

构建 release 版本：

```powershell
.\gradlew.bat :app:assembleRelease
```

Release APK 必须使用自己的 release 密钥签名后才能安装。可以使用以下命令运行项目验证脚本：

```powershell
.\scripts\verify.ps1
```

## 项目结构

```text
app/src/main/java/dev/breenottshook/
├── api/       GPT-SoVITS 客户端、角色目录和诊断
├── audio/     音频解码和格式模型
├── config/    共享配置、校验、编码和持久化
├── hook/      LSPosed 注入、版本路由和宿主适配
├── playback/  AudioTrack 播放和流式写入
├── session/   TTS 会话和取消控制
└── ui/        模块 App 与内嵌设置界面
```

主要入口：[Hook 实现](app/src/main/java/dev/breenottshook/hook)、[配置](app/src/main/java/dev/breenottshook/config)、[设置界面](app/src/main/java/dev/breenottshook/ui) 和 [Android Manifest](app/src/main/AndroidManifest.xml)。

## 诊断与反馈

可以在设置页底部复制诊断日志。提交问题时请提供设备型号、Android/ColorOS 版本、小布版本、LSPosed 版本、复现步骤和脱敏后的诊断记录。
