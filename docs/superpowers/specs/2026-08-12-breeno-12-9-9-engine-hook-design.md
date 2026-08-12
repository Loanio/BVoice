# Breeno 12.9.9 应用内 TTS 引擎兼容设计

## 目的与覆盖关系

本规格修正 `2026-08-12-breeno-11-8-3-12-9-9-compatibility-design.md` 中“12.9.9 仍以 `RealWebSocket.send(String)` 作为 TTS 入口”的旧假设。JADX MCP 与实机脱敏探针已经证明，小布 12.9.9 的回答播报不经过该旧端点。

同一模块 APK 继续同时支持小布 11.8.3 与 12.9.9：11.8.3 保留已经验证的 WebSocket 传输 Hook；12.9.9 改为 Hook 应用内 `TTSEngineImpl`。共享配置、第三方请求、音频解码、AudioTrack 播放和会话协调器不复制。

## 已验证证据

- Vector/YukiHook 冷启动加载成功，12.9.9 发布 `state=active` 与 `settings_active`。
- 小布设置页已实机显示“第三方音色”入口。
- 开启第三方 TTS 并将日志等级设为 DEBUG 后，完整回答期间 `okhttp3.internal.ws.RealWebSocket.send(String)` 没有调用。
- JADX 12.9.9 全局搜索不到 `openapi-slp.heytapmobi.com` 或 `/tts/ws`。
- 普通 TTS 入口是 `com.heytap.speechassist.core.engine.TTSEngineImpl.C0(String, km.w, Bundle, TTSEngine.SlpTtsCallBack)`；`B0(String, km.w, Bundle)`转发到它。
- AI 流式 TTS 调用链为 `AiChatTTSHighlightPlayManager` → `TTSEngineImpl.P0(listener, Bundle)` → 多次 `O0(String)` → `J0()`。
- `O0(String)` 最终调用 `com.heytap.speechassist.sdk.TTSEngine.streamSpeak(String)`；`J0()` 最终调用 `streamEnd()`。
- 原 SDK 提供 `streamCancel`、`streamPause`、`streamResume`、`shutup`、VoiceOutput/StreamTtsStatus 监听器和通知方法。

以上类名与混淆方法名只对精确版本 `12.9.9` 生效；未来版本必须重新验证。

## 第三方 API 契约

兼容层继续调用用户提供的 GPT-SoVITS 服务根地址，当前默认值为 `http://47.111.184.220:5000/`。不调用或代理小布内部云端接口，也不要求第三方服务修改协议。

- `GET {baseUrl}/character_list`：返回角色到情感列表的 JSON 映射。
- `POST {baseUrl}/tts`：请求体继续使用现有字段：`text`、`character`、`emotion`、`text_language`、`format`、`top_k`、`top_p`、`temperature`、`batch_size`、`speed`、`save_temp`、`stream`。
- 响应继续由现有 `GptSovitsClient` 按流读取，并由现有 WAV/PCM 解码与 `AudioTrackSink` 播放。
- URL、超时、音色、情感、格式、采样参数和流式开关均来自模块 APP 与小布设置页共享的同一份配置。
- 明文 HTTP 警告与仅对配置目标主机放行的策略保持不变。

## 版本路由

### 11.8.3

保留现有经过载荷门控的 `RealWebSocket.send(String)` Hook、原请求回退及模块播放器。12.9.9 的引擎描述符不得安装到 11.8.3。

### 12.9.9

`Breeno1299Profile` 声明独立的 `EngineTtsDescriptor`，包含普通说话、流式开始、流式分片、流式结束、取消、暂停和恢复方法的完整参数与返回类型。安装时每个必需方法必须唯一匹配；缺失或多义时停用 12.9.9 TTS 替换并发布脱敏状态，不回退到旧 WebSocket 猜测。

## 普通播报数据流

Hook `TTSEngineImpl.C0(String, listener, Bundle, callback)`，而不是同时 Hook `B0`，避免同一调用被拦截两次。

1. 功能关闭、文本为空或配置无效时执行原方法。
2. 功能开启时阻止原 `C0`，把完整文本提交给共享 `TtsSessionCoordinator`。
3. 第三方播放开始、完成、错误与取消通过一个 `HostCallbackBridge` 映射到 `km.w` 的 `onSpeakStart`、`onSpeakCompleted`、`onTtsError` 或 `onSpeakInterrupted`。
4. 第三方失败且尚未开始播放、并允许原 TTS 回退时，仅调用一次原 `C0`。
5. 第三方音频已经开始后失败，不再播放原句，避免双重播报。

回调桥只调用经 JADX 验证的方法，并对空 listener 安全处理。`SlpTtsCallBack` 的具体成功/失败语义在实现前继续通过 JADX 验证；未验证前不伪造该回调，只在回退时交还原实现。

## 流式播报数据流

流式回答不能把每个 `O0(String)` 分片当成独立 TTS 请求。模块维护单个受锁保护的流式代际：

1. `P0(listener, Bundle)` 开始新的缓冲代际，保存原 listener 与 Bundle，但在第三方模式下不启动原 SDK 流。
2. 每次 `O0(String)` 只追加非空分片，并阻止原 `streamSpeak`。
3. `J0()` 冻结当前缓冲，将分片按原顺序拼接后提交一次第三方合成。
4. 新 `P0`、`streamCancel`、`shutup` 或会话取消会丢弃旧缓冲并取消旧第三方任务。
5. 若第三方在播放前失败且允许回退，则按原顺序执行原 `P0`、每个原 `O0`、最后原 `J0`，完整恢复小布流式 TTS。
6. 播放已开始后失败不执行原流回退。

为防止异常内存增长，缓冲设置字符上限；超过上限视为播放前错误并按配置回退。空流在 `J0` 时直接结束，不调用第三方 API。

## 播放、打断与宿主状态

第一版 12.9.9 仍使用模块 `AudioTrackSink`，因为尚未证明小布播放器存在安全的外部 PCM 注入入口。模块使用助手音频属性并维持已有 generation ID，后到请求取消先到请求。

普通播报通过 `km.w` 回灌开始、完成、错误和打断。流式播报通过保存的 `StreamTtsListener` 与 SDK 状态监听形状回灌；实现前必须用 JADX 验证接口的全部方法签名。无法安全回灌的状态只发布模块诊断，不调用未知反射方法。

原 `streamCancel`、`shutup` 和经验证的停止入口会取消当前 HTTP 请求、解码和 AudioTrack。暂停/恢复优先映射到模块播放器；若现有 Sink 不支持无损暂停，则暂停按取消处理并发布 `cancelled`，不得让原 SDK 与第三方播放器同时发声。

## 配置和诊断

模块 APP 与小布设置页继续共享 ContentProvider 配置。`enabled=false` 时所有新引擎 Hook 原样放行。`forceModulePlayer` 在当前 12.9.9 实现中保持可见；由于安全原播放器适配尚未验证，它不会改变当前路由，但会在状态中明确显示模块播放器被使用。

DEBUG 日志只允许记录能力、方法命中、分片数量、总字符数和文本摘要哈希。禁止记录完整文本、角色服务响应、令牌、Cookie、Bundle 敏感值或完整请求体。

## 测试与验收

所有生产修改先写失败测试。

- 描述符测试：12.9.9 方法形状完整且唯一；11.8.3 不安装引擎 Hook。
- 普通播报测试：关闭放行、开启拦截、只提交一次、播放前回退、播放后不回退、回调顺序。
- 流式缓冲测试：开始/多分片/结束只合成一次；取消清空；新代际淘汰旧代际；空流；超限；回退严格保持原分片顺序。
- 并发测试：结束与取消竞争时最多一个终态；旧合成回调不能影响新代际。
- API 回归测试：仍请求 `/tts`，现有请求字段和值映射不变；`/character_list` 不变。
- 完整单元测试、Lint、Debug 构建通过。
- 实机 12.9.9：Vector 重载后出现 `engine=true`；普通播报与流式回答至少各命中一次 `intercepted`；观察到对 `47.111.184.220:5000` 的请求；第三方音频可听；停止、连续提问、服务离线回退和配置热更新通过。
- 11.8.3：自动化与 APK 结构回归通过；若无法在当前设备切换预装版本，明确标记未做实机播放回归。

## 非目标

- 不 Hook 全局 `com.heytap.speechassist.sdk.TTSEngine` 的所有调用，以免影响唤醒训练、AI 通话和其他非助手场景。
- 不直接修改 Vector 数据库。
- 不伪造尚未验证的内部回调。
- 不把 12.9.9 的旧 WebSocket 作为备用猜测路径。
- 不修改用户的 GPT-SoVITS 服务端。
