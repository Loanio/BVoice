# 诊断说明

## 模块 APP

调试区显示共享配置版本、连接测试结果和最近状态。配置保存成功后版本号应增加；如果两侧同时修改造成版本冲突，编辑器会重新加载最新值，避免静默覆盖。

## Hook 状态

Hook 通过受限 `ContentProvider` 写入简短状态：

- `active`：11.8.3 传输回退已安装。
- `intercepted / playing / completed`：第三方会话阶段。
- `failed / cancelled / fallback_failed`：失败、打断或原 TTS 恢复失败。
- `unsupported / disabled`：版本、类或方法不唯一，安全停用。
- `settings_disabled`：没有已验证设置宿主；模块 APP 仍可配置。

播报只以 `chars=<长度>,sha256=<12位摘要>` 标识，不含原文。不要把 API 密钥、Cookie 或完整对话写入问题报告。

## 常见问题

### Hook 显示 unsupported

确认小布版本为 11.8.3，并确认 LSPosed 作用域只有 `com.heytap.speechassist`。其他版本不会尝试模糊匹配。

### 连接测试成功但小布仍用原音色

检查“启用第三方 TTS”、严格模式和 Hook 状态。若 `RealWebSocket` 类路径与目标 APK不同，需要提供 APK/JADX 结果更新版本描述符。

### 小布无声

先关闭严格模式并开启原 TTS 回退。若第三方已经开始播放后失败，系统不会重播原语音，以免双重播报；检查 WAV 格式、服务响应和 AudioTrack 诊断。

### HTTP 被拒绝

模块只为当前配置的 HTTP 主机做精确明文放行。优先改用 HTTPS；不要全局关闭 Android 网络安全策略。

### 收集信息

记录小布版本、ColorOS 版本、Hook 状态、异常类型和时间点。完整文本与服务凭据应先删除或脱敏。
