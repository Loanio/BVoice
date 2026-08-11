# BreenoTTSHook

面向 Android 15 / ColorOS 15 的 Yuki Hook API + LSPosed 模块，用 GPT-SoVITS 替换小布助手（`com.heytap.speechassist`）进入同一 TTS 传输组件的播报。

## 当前能力

- 目标版本：小布助手 11.8.3。
- 精确拦截 HeyTap TTS WebSocket：`wss://openapi-slp.heytapmobi.com/tts/ws`（允许签名查询参数，但严格校验协议、主机和路径）。
- 默认 GPT-SoVITS API：`http://47.111.184.220:5000/`，支持 `POST /tts` 与 `GET /character_list`。
- 流式、拼接 WAV 解码；模块 `AudioTrack` 播放、音频焦点、取消与代际隔离。
- 第三方首个 PCM 播放前可按配置恢复原 TTS；严格调试模式禁止静默回退。
- 模块 APP 提供全部 23 个配置项、动态角色/情感、手动值、连接测试、试听与停止。
- 导出的 `ContentProvider` 仅授权模块自身和小布包名；两侧使用同一份带版本号的原子配置。
- 普通诊断只记录字符数和文本 SHA-256 前缀，不记录完整播报文本。

## 重要兼容性边界

当前没有连接设备或小布 11.8.3 APK 可供 JADX 验证，因此没有猜测业务 TTS 方法、原播放器方法或设置 Activity/Fragment。现版本自动启用的是 URL 门控的 `RealWebSocket` → GPT-SoVITS → `AudioTrack` 路径；原播放器和小布设置页入口保持能力禁用。原生全量设置对话框及安全注入安装器已经实现，取得并验证宿主类描述符后才会启用。

这意味着 APK 已完成编译和自动测试，但仍需要在目标设备上做 LSPosed 实机验证。详见 [兼容性说明](docs/COMPATIBILITY.md)。

## 构建

要求 JDK 17 与 Android SDK 35：

```powershell
.\scripts\verify.ps1
```

脚本依次运行单元测试、Lint 和 Debug APK 构建。安装与恢复步骤见 [安装说明](docs/INSTALL.md)，排障见 [诊断说明](docs/DIAGNOSTICS.md)。

## 安全提醒

默认 API 使用 HTTP，播报文本和音频没有传输加密。模块 APP 只对默认主机放行明文；注入进程只对当前配置中的 HTTP 主机做精确放行。建议在可信局域网使用或把服务迁移到 HTTPS。
