# 兼容性与验证状态

| 层级 | 状态 | 说明 |
|---|---|---|
| 编译 | 已验证 | JDK 17、compile/target SDK 35、Yuki Hook API 1.2.1 |
| JVM 单元/Robolectric | 已验证 | 配置、IPC 授权、API、WAV、会话、Compose、Hook 门控、原生字段工厂 |
| URL 门控传输回退 | 自动测试通过 | 只接受 `wss` + 精确 HeyTap 主机 + `/tts/ws`，允许签名查询参数 |
| 模块 AudioTrack 播放 | 编译和状态机测试通过 | 尚未在 ColorOS 15 真机验证音频焦点与中断 |
| 11.8.3 业务 TTS 入口 | 禁用 | 无 APK/JADX 证据，不猜描述符 |
| 小布原播放器 | 禁用 | 无播放器方法和完成回调证据；当前使用模块播放器 |
| 小布设置页注入 | 安装器已实现、默认禁用 | 原生全量编辑器已完成；宿主描述符列表为空，等待 APK 验证 |
| Android 15 / ColorOS 15 实机 | 未验证 | 当前 ADB 无连接设备 |
| 公共 GPT-SoVITS API | 已冒烟验证 | 2026-08-11，仅使用固定短语，不发送小布历史 |

## 需要从真机/APK确认的描述符

1. 小布版本名/版本号确实为 11.8.3。
2. TTS WebSocket 的 `RealWebSocket.send(String)` 类路径和 JSON 文本字段。
3. 业务 TTS 请求、停止/打断入口。
4. 原播放器的 PCM/音频入口、音频焦点行为与完成/错误回调。
5. 设置 Activity/Fragment 的精确类名与 `onCreate`/视图生命周期。

在这些信息确认前，`Breeno1183Profile` 明确报告 `originalPlayer=false`、`businessTtsEntry=false`，`Breeno1183SettingsHosts.descriptors` 保持空列表。

## 公共 API 冒烟结果

- `GET /character_list`：HTTP 200，共 12 个角色；测试自动选择“八重神子 / default”。
- `POST /tts` 非流式：HTTP 200，约 971 ms，`audio/wav`，162604 字节。
- `POST /tts` 流式：HTTP 200，约 995 ms，`audio/wav`，170284 字节。
- 两种响应均为单段 `RIFF/WAVE`，PCM format tag 1、32000 Hz、单声道、16 bit。
- 测试文本固定为“你好，这是连接测试。”；临时音频在解析后立即删除。

## APK 校验

`BreenoTTSHook-debug.apk`

```text
SHA-256 dabe4d5807f10b2d30019b173d64210e5fe85bc0020101bf87336fb4733c6ef1
```
