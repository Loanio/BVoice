# 兼容性与验证状态

| 层级 | 状态 | 说明 |
|---|---|---|
| 编译 | 已验证 | JDK 17、compile/target SDK 35、Yuki Hook API 1.2.1 |
| JVM 单元/Robolectric | 已验证 | 配置、IPC 授权、API、WAV、会话、Compose、Hook 门控、原生字段工厂 |
| URL 门控传输回退 | 自动测试通过 | 只接受 `wss` + 精确 HeyTap 主机 + `/tts/ws`，允许签名查询参数 |
| 模块 AudioTrack 播放 | 真机端到端验证 | ColorOS 15 / Android 15 已验证第三方 WAV 播放链路 |
| 11.8.3 传输 Profile | 静态验证 | APK 为 11.8.3（110803），存在 `RealWebSocket.send(String): boolean`、`cancel()`、`close(int,String)` |
| 12.9.9 Engine Profile | 真机验证 | APK 为 12.9.9（120909），普通 `D0` 与流式 `G/O0/J0` 已命中；“播报全文”无 `G` 时走隐式流 |
| 小布原播放器 | 禁用 | 无播放器方法和完成回调证据；当前使用模块播放器 |
| 小布设置页注入 | 安装器已实现、默认禁用 | 原生全量编辑器已完成；宿主描述符列表为空，等待 APK 验证 |
| Android 15 / ColorOS 15 实机 | 已验证 | OnePlus PLC110，`192.168.0.102:6666`；新版 APK 已安装，普通播报、流式播报及“播报全文”均完成 Hook 链路验证 |
| 公共 GPT-SoVITS API | 已冒烟验证 | 2026-08-11，仅使用固定短语，不发送小布历史 |

## 需要从真机/APK确认的描述符

1. 两个版本实际 TTS JSON 的完整、脱敏字段组合。
2. 业务 TTS 请求、停止/打断入口。
3. 原播放器的 PCM/音频入口、音频焦点行为与完成/错误回调。
4. 设置 Activity/Fragment 的精确类名与 `onCreate`/视图生命周期。

在这些信息确认前，两个 Profile 均明确报告 `originalPlayer=false`；11.8.3 保持 WebSocket 回退，12.9.9 报告 `businessTtsEntry=true` 并走 Engine 路由，未来版本不自动命中。

## 目标 APK 静态证据

- 11.8.3：`versionCode=110803`、`versionName=11.8.3`，预装基线位于 `/product/priv-app/HeyTapSpeechAssist`。
- 12.9.9：`versionCode=120909`、`versionName=12.9.9`，当前更新版位于 `/data/app`。
- 两个 APK 均验证 `okhttp3.internal.ws.RealWebSocket` 的公开 `send(java.lang.String): boolean`、`cancel(): void`、`close(int, java.lang.String): boolean`。

## 公共 API 冒烟结果

- `GET /character_list`：HTTP 200，共 12 个角色；测试自动选择“八重神子 / default”。
- `POST /tts` 非流式：HTTP 200，约 971 ms，`audio/wav`，162604 字节。
- `POST /tts` 流式：HTTP 200，约 995 ms，`audio/wav`，170284 字节。
- 两种响应均为单段 `RIFF/WAVE`，PCM format tag 1、32000 Hz、单声道、16 bit。
- 测试文本固定为“你好，这是连接测试。”；临时音频在解析后立即删除。

## 2026-08-16 真机回归

- 设备：OnePlus PLC110，Android 15 / ColorOS 15，小布 12.9.9。
- Vector 作用域：`dev.breenottshook` → `com.heytap.speechassist`。
- 普通引擎入口命中 `TTSEngineImpl.D0`；流式回答命中 `G/O0/J0`。
- 菜单“播报全文”只发送 `O0` 分片和 `J0`，不发送流开始方法；实现已增加隐式流代际，在 `J0` 合并文本后只提交一次 `/tts`。
- 日志确认该路径命中 `engine_stream_chunk` 与 `engine_stream_end`；修复后由第三方会话接管。
- 取消路径的 `cancel` 命名冲突已修复为 `cancelHandler`，回归测试确认只调用一次。
- Debug 单元测试与 `assembleDebug` 均通过，APK 已通过 ADB 安装验证。
- 句子高亮：流式文本按中英文标点/换行拆分为有序 utterance；每句首次写入非空 PCM 后回调
  `onUtteranceStarted(index)`，由宿主 listener 切换当前句颜色；取消、代际淘汰和失败回退均保持 exactly-once。
- 播放时序：`AudioTrackSink` 在首个非空 PCM 写入前启动 AudioTrack 并同步写入，确保高亮回调不早于实际播放；后续句子复用同一 AudioTrack 连续播放。

## APK 校验

`BreenoTTSHook-debug.apk`

```text
SHA-256 3953d1205dc557d30450514549d7094502f555ebafd246ab14039364023fac66
```
