# 安装与使用

## 环境要求

- Android 12 或更高版本
- Magisk 或 KernelSU
- LSPosed
- 小布助手 `com.heytap.speechassist`
- GPT-SoVITS 服务，提供 `GET /character_list` 和 `POST /tts`

## 安装

1. 安装 BreenoTTSHook APK。
2. 在 LSPosed 中启用模块。
3. 作用域仅选择小布助手：`com.heytap.speechassist`。
4. 强制停止并重新启动小布助手。
5. 打开模块 App，或在小布设置中打开“第三方音色”。

## 配置

1. 填写 GPT-SoVITS 服务地址。
2. 加载角色与情感，或启用手动音色填写对应值。
3. 使用“测试连接”和“试听”检查服务与音频播放。
4. 开启“启用第三方 TTS”。

配置自动保存，并在模块 App 与小布内嵌设置页之间同步。

## 安全停用

如需恢复小布原始 TTS，在 LSPosed 中取消 BreenoTTSHook 对小布助手的作用域，然后重启小布助手。模块不会修改小布 APK。

## 网络安全

服务地址由用户自行配置。建议使用 HTTPS；使用 HTTP 时，请仅在可信网络中传输测试内容。
