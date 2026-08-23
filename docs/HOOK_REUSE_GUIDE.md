# Android TTS Hook 复用手册

本文沉淀 `BreenoTTSHook` 在 Android 15 / ColorOS 15、Vector（LSPosed 兼容管理器）与小布助手上的实机经验。它面向类似的“宿主 TTS → 第三方 HTTP TTS → PCM 播放”项目。

> 先区分两类信息：协议、播放器、配置同步模式可直接复用；宿主类名、混淆方法名、Fragment 名称和方法描述符只能作为版本 Profile 样例，升级 APK 后必须重新验证。

## 1. 目标架构与边界

```text
宿主业务 TTS 方法
  │  （严格的版本 Profile）
  ▼
Hook 回调桥 / 会话状态机
  ├─ 第三方不可用且未播放 PCM：exactly-once 恢复原调用
  └─ 第三方可用
       ▼
HTTP TTS 客户端 ──> 流式 WAV/PCM 解码 ──> AudioTrack + 音频焦点
       ▲                                                │
       └──── 受权限保护的 ContentProvider 配置 ─────────┘
                 ▲                         ▲
              模块 App                 宿主设置页注入
```

核心原则：只 Hook 已验证的宿主业务实现类；不要 Hook 公共 SDK 接口、系统 `TextToSpeech`、`AudioTrack` 或网络栈的全局类。全局 Hook 会误伤唤醒训练、系统播报或其他应用。

## 2. 版本 Profile：宁可停用，也不要猜

每一个宿主版本都用一个 Profile 表示，Profile 至少包含：

- 宿主包名、`versionCode` / `versionName`；
- 业务实现类的完整类名；
- 每个目标方法的名称、参数类型完整列表、返回类型；
- 普通 TTS 与流式 TTS 的调用顺序；
- 回调接口的方法及宿主异常构造方式。

解析器必须同时校验方法名、参数列表、返回类型，并要求唯一命中。缺少、重载或签名改变时，整组功能停用并记录 `engine descriptor mismatch`，而不是“按参数数量猜一个”。

### 小布 12.9.9 样例（仅作重新验证的起点）

已分析的业务实现类为：

```text
com.heytap.speechassist.core.engine.TTSEngineImpl
```

候选业务签名：

```text
m39754C0(String, km.w, Bundle,
         com.heytap.speechassist.sdk.TTSEngine$SlpTtsCallBack): void

m39779P0(StreamTtsListener, Bundle): void     // start
m39777O0(String): void                         // chunk
m39768J0(): void                               // end
```

流式顺序是 `start → chunk* → end`。但是混淆名和描述符随 APK 更新可变；运行日志出现 `engine descriptor mismatch;candidates=...` 时，应以候选列表和 JADX 结果更新 Profile，而不是放宽匹配。

11.8.3 与 12.9.9 的传输路径不同：旧版可从特定 WebSocket 路径路由；新版应走 `TTSEngineImpl`。不要让旧版 WebSocket 规则误认为新版适配成功。

## 3. 回调与回退：保证不会双播

会话层需要保存原始调用所需的全部数据：普通调用的 callback，流式调用的 start、每个原始 chunk、end，以及 Bundle / listener。

回退规则：

1. 第三方请求、解析或打开播放器在**第一个 PCM 字节播放前**失败，且 `fallbackToOriginal=true`、`strictMode=false`：按原顺序恢复原调用一次。
2. 第一个 PCM 已经写入后，不再恢复原句；只回调错误并释放播放器。否则用户会听到前半段第三方音色加完整原声的双播。
3. 取消、完成、错误都需要单次终态保护（`AtomicBoolean` 或同等机制），新请求必须取消旧代次。

普通回调常见形态：`onSpeakStart()`、`onSpeakCompleted()`、`onSpeakInterrupted(int)`、`onTtsError(int, String)`；流式回调常见形态：`onSpeakBegin()`、`onEnd()`、`onCompleted(SpeechException?)`。宿主异常应通过经验证的构造器反射构造；找不到构造器时停止该版本 Profile。

## 4. 共享配置：模块 App 和宿主设置用同一份数据

模块使用导出的 `ContentProvider` 保存带版本号的原子配置快照：

- 模块 App 负责编辑、试听、角色列表刷新和调试开关；
- 宿主进程只通过 `ContentResolver.call(...)` 读取配置与写入 Hook 状态；
- Provider 仅允许模块自身 UID 与目标宿主包访问；
- 不用 `adb shell content query` 读取这类 Provider：shell UID 不在授权名单，失败是正常的；
- 不要修改 Vector/LSPosed 数据库。启用状态由 Vector UI 管理。

用户可见配置需要包括：启用开关、URL、角色、情感、语言、`top_k`、`top_p`、`temperature`、批大小、语速、缓存、`stream`、超时、回退、严格模式、播放器开关、日志等级和试听文本。保存时对 URL、超时、数值范围做校验。

## 5. 宿主设置页注入：以 Fragment 为主

许多宿主的设置 Activity 只是容器，真正的内容由 Preference Fragment 在稍后创建。仅在 Activity `onCreate` 加 View，往往会被 Fragment 覆盖，表现为“栏目不见”。

可靠流程：

1. 用 JADX 找出设置 Activity、真实 Fragment 和其 `onViewCreated(View, Bundle)`；
2. 在 Fragment 的 `onViewCreated` 后拿到根 `PreferenceScreen`；
3. 递归遍历嵌套的 `PreferenceGroup`，而非只遍历根节点；
4. 以稳定锚点（本项目为“`小布音色`”）的**父分组**为插入位置；
5. 添加一个原生 `Preference`，标题“第三方音色”，点击后展示/跳转模块的设置页；
6. Activity 注入只保留为兜底，不承担主要逻辑。

小布 12.9.9 的已验证入口样例：

```text
Activity: com.heytap.speechassist.home.settings.p294ui.SettingsActivity
Fragment: com.heytap.speechassist.home.settings.p294ui.fragment.SpeechSettingFragment
```

### 反射崩溃陷阱

`Preference.setSummary` 同时存在 `setSummary(int)` 与 `setSummary(CharSequence)`。反射时按名称取第一个重载可能会把 `String` 传给 `int`，导致设置页崩溃。必须按唯一参数类型精确选择 `CharSequence` 重载，并用 `runCatching` 保护注入逻辑，保证反射失败不会杀死宿主进程。

## 6. 第三方 TTS HTTP 契约

服务地址由用户配置。端点：

```text
GET  /character_list
POST /tts
```

`POST /tts` 兼容字段：

```json
{
  "text": "...",
  "character": "...",
  "emotion": "...",
  "text_language": "...",
  "top_k": 5,
  "top_p": 1.0,
  "temperature": 1.0,
  "batch_size": 1,
  "speed": 1.0,
  "save_temp": false,
  "stream": true,
  "format": "wav"
}
```

前 11 个字段不能删除或改名；`format` 为播放器协商用的额外字段。不得在日志输出正文、角色列表全文、Token、Cookie 或完整 JSON。调试只记录状态码、Content-Type、字节数、配置格式、前 4–12 字节十六进制和文本的 `chars + SHA-256 前缀`。

HTTP 客户端必须先处理：非 2xx、空 body、响应上限、取消时取消 OkHttp Call、`application/json` / `text/*` 错误正文。不要把错误 JSON 直接交给 WAV 解码器。

## 7. 流式 WAV：实机协议与正确解码

本服务在 `stream=true`、`format=wav` 时返回 `Content-Type: audio/wav`，但不是完整长度的常规 WAV。实际首部为：

```text
52 49 46 46 24 00 00 00 57 41 56 45   RIFF$...WAVE
66 6D 74 20 10 00 00 00 ...             fmt chunk
64 61 74 61 00 00 00 00                 data chunk, length = 0
<后续持续追加裸 PCM>
```

即 RIFF size 为 `36`，data size 为 `0`，最终长度未知。常规 WAV 解码器若把 44 字节头当成完整文件，下一包 PCM 会被当成新的 RIFF，报 `Missing RIFF signature`。

可复用的解码状态机：

1. 缓冲至少 44 字节；验证 `RIFF`、`WAVE`、PCM `fmt `、`RIFF size == 36`、`data size == 0`；
2. 解析采样率、声道数、位深，切换到 `streamingFormat`；
3. 移除 44 字节头；之后每一网络块都以该格式直接产出 `PcmSegment`；
4. `finish()` 在流式模式下正常完成，而不是把末尾 PCM 判为截断；
5. 常规 WAV、拼接 WAV、未知 chunk 与截断检测仍保留原逻辑。

本服务样本的格式为 `32000 Hz / mono / PCM 16-bit`。不要将其硬编码到播放器；以 WAV `fmt` chunk 为准。

当前模块播放器只实现 PCM/WAV。若 UI 暴露 MP3、OGG、裸 PCM，必须：要么提供对应解码器和裸 PCM 的采样格式元数据，要么在试听前给出明确“不支持”的错误。不能把它们送进 WAV 解码器。

## 8. AudioTrack 流式播放

播放器创建时按 `PcmFormat` 配置：声道掩码、PCM encoding、采样率、`MODE_STREAM`、音频属性 `USAGE_ASSISTANT / CONTENT_TYPE_SPEECH`，并在创建前请求 `AUDIOFOCUS_GAIN_TRANSIENT`。

Android 15 / ColorOS 15 实测中，流式写入的 `AudioTrack.write(..., WRITE_BLOCKING)` 可以短暂返回 `0`。它表示当前没有字节写入，不等同于负错误码。若立即将其视为失败，`stream=true` 会报 `AudioTrack write failed: 0`，而 `stream=false` 正常。

推荐写入循环：

- `written > 0`：推进 offset；
- `written == 0`：延迟 25ms 后重试，最多 40 次（约 1 秒），延迟使用可取消协程；
- `written < 0` 或持续为 0：失败并释放 AudioTrack；
- 完成时用“写入帧数 / 采样率 + 容差”等待播放头，随后释放焦点与 Track。

这个重试策略只解决瞬时背压；它不应掩盖音频焦点丢失、Track 被释放或格式错误。

## 9. JADX MCP 与 ADB 排障流程

### JADX MCP

先列出能力，再查类和源码。避免假定工具名：本环境没有 `search_text`，使用 `search_classes_by_keyword`。

```powershell
& 'D:\jadx-mcp-server-v6.2.0\jadx-mcp-server\.venv\Scripts\python.exe' `
  'C:\Users\27623\Documents\Codex\2026-08-11\new-chat\jadx_mcp_call.py' `
  __list__ '{}'

& 'D:\jadx-mcp-server-v6.2.0\jadx-mcp-server\.venv\Scripts\python.exe' `
  'C:\Users\27623\Documents\Codex\2026-08-11\new-chat\jadx_mcp_call.py' `
  search_classes_by_keyword `
  '{"search_term":"Settings","package":"com.heytap.speechassist","search_in":"class,code","offset":0,"count":100}'
```

优先读取 `get_methods_of_class`、`get_class_source`、`get_method_by_name` 和 Manifest/Fragment 生命周期，再写 Profile。

### ADB / Vector

```powershell
$adb = 'C:\Users\27623\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb devices -l
& $adb -s <serial> shell getprop ro.build.version.release
& $adb -s <serial> shell dumpsys package com.heytap.speechassist
& $adb -s <serial> install -r app-debug.apk
```

每次更新 Hook APK 后：

1. 在 Vector UI 关闭再开启模块；
2. 确认作用域包含目标宿主包与所需进程；
3. 强停并冷启动宿主；
4. 触发一次真实播报；
5. 使用 `logcat` 仅筛选模块 Tag 与异常关键词。

不要把完整播报内容从 `logcat` 导出或贴入问题单。PowerShell 环境中 `Select-String -First` 可能不可用，筛选时可用 `findstr` 或不带 `-First`。

## 10. 最小回归清单

每个改动先写失败测试，再最小实现。至少覆盖：

- Profile 严格匹配、版本路由和描述符不匹配时整体停用；
- 原始回退只发生在首个 PCM 前，且仅一次；
- 普通 WAV、分片 WAV、拼接 WAV、流式零长度 WAV 头、截断 WAV；
- AudioTrack 临时 `write() == 0` 会重试，负返回值会失败；
- 设置 Preference 能在嵌套分组找到锚点，`setSummary(CharSequence)` 重载被正确选择；
- HTTP 请求保留兼容字段，取消会取消 Call，超大响应被拒绝。

构建验证：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

单元测试不能替代实机：最终还需要验证 Vector 作用域、设置页入口、试听的流式/非流式两种模式、宿主真实播报、打断和回退。

## 11. 复用时的检查表

- [ ] 已记录设备、Android、宿主包和宿主版本。
- [ ] 已用 JADX 验证实现类、完整方法描述符和生命周期。
- [ ] Profile 唯一匹配；不匹配时安全停用。
- [ ] 原调用保存完整，回退在 PCM 前 exactly-once。
- [ ] 设置入口注入真实 Fragment，并以嵌套 Preference 锚点插入。
- [ ] 模块与宿主使用同一受权限保护的配置源。
- [ ] 已确认服务端 Content-Type、首包魔数、采样格式和流式封包模式。
- [ ] 解码器与播放器支持所选输出格式。
- [ ] `AudioTrack.write()` 的临时 0 返回得到有限、可取消重试。
- [ ] 日志没有文本、鉴权信息、Cookie 或完整请求体。
- [ ] APK 更新后已在 Vector 重载并冷启动宿主，完成实机回归。
