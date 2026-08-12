# BreenoTTSHook 11.8.3 与 12.9.9 双版本兼容设计

## 目标

同一个 BreenoTTSHook APK 在 Android 15 / ColorOS 15 上同时支持小布助手 `com.heytap.speechassist` 11.8.3（110803）和 12.9.9（120909）。适配范围包含第三方 TTS 替换、Vector 模块加载、原播放器与打断生命周期衔接、模块 APP 与小布设置页共享全部配置，以及安全回退。

本规格是 `2026-08-11-breeno-third-party-tts-design.md` 的增量兼容规格。两者冲突时，以本文对目标版本和已验证事实的描述为准。

## 已验证事实

- 设备为 OnePlus PLC110，Android 15 / ColorOS 15。
- 当前 Vector 管理器包名为 `org.matrix.vector.manager`，版本 2.2（3080）。模块仍通过标准 Xposed/YukiHookAPI 接口加载，不依赖 Vector 内部数据库或私有 API。
- 小布 11.8.3 是 `/product/priv-app/HeyTapSpeechAssist` 中的预装基线；12.9.9 是当前 `/data/app` 生效更新版。
- 12.9.9 APK 已从设备拉取并检查。它仍包含 `okhttp3.internal.ws.RealWebSocket`，且唯一公开 `send(String): Boolean`、`cancel()` 和 `close(Int, String): Boolean` 签名仍存在。
- 现有实现的 `ProfileSelector` 只注册 `Breeno1183Profile`，所以 12.9.9 必然被判为 `unsupported`。
- 功能 worktree 存在尚未提交的 YukiHook 生成入口与延迟 Context 安装修复；后续实施必须保留、测试并单独审阅这些用户工作，不能覆盖或回退。
- 小布原播放器入口、停止入口和 12.9.9 设置页宿主尚未完成精确验证，因此不得在设计中假定其可用。

## 成功标准

- Vector 能识别模块，作用域为 `com.heytap.speechassist`，11.8.3 和 12.9.9 冷启动时均能加载模块入口。
- 两个版本分别命中唯一的版本 Profile；任何结构探测失败或多义时，只停用对应能力，不执行模糊 Hook。
- 所有经已验证目标 TTS 组件发送的播报请求可由第三方服务生成音色；非 TTS WebSocket 不受影响。
- 优先把第三方音频接入已验证的小布原播放器，并复用音频焦点、停止、打断和完成回调；未验证或失配时使用模块 AudioTrack 播放器。
- 用户可通过调试开关强制使用模块播放器，以便区分网络、解码与宿主播放器故障。
- 模块 APP 与注入的小布设置入口均可查看和修改全部配置，且使用同一 ContentProvider 数据、角色缓存、校验规则和 `configVersion`。
- 失败发生在第三方音频开始播放前时，按用户开关回退原 TTS；播放开始后不重复整句。
- 普通日志不记录完整播报文本，只记录版本 Profile、能力状态、请求代际、错误类别和必要的脱敏诊断。
- 自动化测试、Lint、Debug 构建通过，并在当前 12.9.9 设备上完成 Vector 加载、配置、试听和实际播报验收。11.8.3 若当前不能切换实机版本，至少需要 APK 结构验证与自动化回归，并明确报告未完成实机回归。

## 架构

共享层保持版本无关：`GptSovitsClient`、WAV 解码、请求代际、回退策略、AudioTrack 播放、配置 Provider、模块设置 UI 和宿主设置对话框不复制。

版本相关知识集中在 Profile：

```text
HookEntry / Vector
  -> 等待小布 Application Context
  -> ProfileSelector(versionName + class/method probes)
       -> Breeno1183Profile
       -> Breeno1299Profile
  -> CapabilityInstaller
       -> transport interception
       -> original-player adapter
       -> stop/cancel bridge
       -> settings host injection
  -> shared TTS/runtime/config components
```

每个 Profile 声明版本号、传输类与方法形状、请求识别规则、原播放器描述符、停止描述符、设置宿主描述符及能力说明。安装器必须使用所选 Profile 的描述符，不能继续硬编码 `Breeno1183Profile.REAL_WEB_SOCKET_CLASS`。

## Profile 选择与能力级熔断

### 精确版本门控

稳定 Profile 仅精确匹配 `11.8.3` 或 `12.9.9`。未来版本即使保留相同 OkHttp 类，也默认显示 `unsupported`，避免未经验证的全局网络 Hook。

### 结构探测

版本匹配后再验证目标结构：类必须唯一存在，方法名、参数和返回类型必须完全匹配。TTS 传输能力还必须在运行时检查请求载荷是否满足经过验证的 TTS 协议特征；普通 WebSocket 消息原样放行。

### 独立能力状态

传输替换、原播放器、停止桥和设置注入分别安装并发布状态。例如设置宿主失配不得阻止 TTS 替换；原播放器失配必须自动降级到 AudioTrack。状态包含 `active`、`fallback`、`unsupported`、`ambiguous` 或 `disabled` 以及脱敏原因。

## Vector 与 YukiHook 入口

使用 YukiHookAPI 注解处理器生成兼容的 Xposed 入口，并在 `xposed_init` 指向实际生成类。入口测试必须从打包后的资源读取类名，验证该类存在并实现传统加载器要求的接口。

小布进程早期 `appContext` 为空时，安装推迟到 Application `onCreate`，并保证重复生命周期回调只安装一次。模块不读取或修改 Vector 数据库；启用、作用域和日志操作全部通过 Vector UI 或标准框架行为完成。

## TTS 传输与请求生命周期

11.8.3 和 12.9.9 当前均可使用 `RealWebSocket.send(String)` 作为保守传输回退，但只有载荷被协议解析器确认为 TTS 请求时才拦截。解析失败、字段缺失或非 TTS 请求必须调用原方法。

拦截后沿用已有 generation ID 状态机。新请求取消旧 HTTP 任务和旧播放；`cancel()`、`close()` 以及经版本验证的小布停止入口取消当前代际。网络、解码和播放回调必须携带代际，旧回调不得改变当前会话。

当第三方功能关闭、配置无效、Profile 未命中或请求不属于 TTS 时，原 WebSocket 行为保持不变。第三方失败时仅在尚未开始播放且“原 TTS 回退”开启时恢复原发送。

## 原播放器兼容层

原播放器通过 `OriginalPlayerDescriptor` 描述，不把反射细节写入共享协调器。每个描述符需要证明：

- 输入音频格式和所有权；
- 音频焦点由谁申请和释放；
- 完成、错误与取消回调形状；
- 停止方法是否只影响当前 TTS；
- 连续请求和打断时的线程约束。

只有类、方法和回调全部唯一匹配且实机播放通过，Profile 才将 `originalPlayer=true`。否则 `PlaybackRouter` 自动使用 `ModuleAudioTrackSink`。保留“强制模块播放器”调试开关，开启时跳过原播放器；关闭时仍允许能力级自动降级。

如果 12.9.9 与 11.8.3 的原播放器描述符相同，可以共享描述符对象，但两个 Profile 必须分别声明并分别验收，不能因代码相同而推导兼容。

## 双端共享配置与设置注入

模块 APP 继续提供全部配置项。小布设置页注入只选择与当前 Profile 完全匹配且已验证的 Activity 或 Fragment 描述符；找不到唯一宿主时发布 `settings_disabled`，不向任意页面添加 View。

注入入口打开 `HostSettingsDialog`，展示与模块 APP 相同的全部字段，而不是简化子集。两端通过同一受限 ContentProvider 原子读取和写入：只允许模块包和小布包访问；成功保存递增 `configVersion` 并发送变更通知；小布热路径使用最后有效内存快照。

设置宿主只负责入口生命周期，不复制配置状态。角色刷新、手动角色/情感、URL、超时、流式参数、采样与播放选项、回退开关、严格模式、播放器调试开关和日志等级都必须双向一致。

## 错误处理与安全边界

- Profile 不匹配：保持原 TTS，状态为 `unsupported`。
- 结构探测多义：只停用该能力，状态为 `ambiguous`。
- 第三方请求或解码在播放前失败：按回退开关恢复原请求。
- 第三方音频已经开始：失败后终止，不重播整句。
- 原播放器失败：切换到模块播放器；若模块播放器也失败，再按播放是否开始决定是否可回退。
- 设置宿主失配：保留模块 APP 配置能力，不影响播报替换。
- 明文 HTTP 只对用户配置的目标主机放行，界面持续显示未加密提示。
- 日志不得输出完整文本、账号令牌、Cookie 或 WebSocket 完整载荷。

## 测试策略

先写失败测试，再改生产代码。

单元测试覆盖：两个精确版本分别命中正确 Profile；未来版本不自动命中；传输安装使用所选 Profile 描述符；方法缺失或多义时能力熔断；非 TTS WebSocket 原样放行；播放前/后回退差异；原播放器失配降级；强制模块播放器开关；两个设置宿主描述符的选择。

Robolectric/构建测试覆盖：YukiHook 生成入口可由 `xposed_init` 加载；Context 延迟安装只执行一次；Provider 双包鉴权；双端全部字段一致；保存通知与配置版本递增；试听完成、错误和取消后 UI 状态自动复位。

静态 APK 验证记录两个版本的目标类和方法证据。设备验收对 12.9.9 执行：Vector 加载、Profile 状态、角色刷新、试听、正常问题播报、连续提问、播放中打断、服务不可用回退、严格模式、强制模块播放器、配置热更新、小布重启和模块 APP 被杀后的持久化。

## 实施边界

先把现有未提交的生成入口和延迟安装修复纳入测试并单独提交，再增加 12.9.9 Profile。随后验证设置宿主和原播放器；如果 APK 静态分析无法证明唯一且安全的入口，该能力保持降级状态，不能用猜测填充描述符。

“全部适配”表示所有能力都有明确且安全的双版本行为：已验证能力启用，未验证能力明确降级并保留其替代路径。它不允许为追求状态全绿而 Hook 任意页面、全局网络或未知播放器。
