# Breeno 11.8.3 与 12.9.9 双版本兼容 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个在 Android 15 / ColorOS 15 上同时兼容小布助手 11.8.3 和 12.9.9 的 BreenoTTSHook APK，覆盖 Vector 加载、第三方 TTS、播放器降级、双端共享配置和安全设置注入。

**Architecture:** 共享 GPT-SoVITS、WAV、会话、AudioTrack 和配置层保持不变；版本差异由精确版本 Profile 和能力描述符承载。每项能力先做唯一结构探测，再独立安装或熔断，任何不确定性都回到原 TTS 或模块 APP，而不是执行模糊 Hook。

**Tech Stack:** Kotlin、Android 15、YukiHookAPI 1.2.1、Xposed/Vector、Coroutines、OkHttp、Compose Material 3、JUnit4、Robolectric、Gradle。

## Global Constraints

- 支持版本仅为 `11.8.3`（110803）和 `12.9.9`（120909）；未来版本默认 `unsupported`。
- Vector 管理器包名是 `org.matrix.vector.manager`，但模块不得依赖其数据库或私有 API。
- 目标包始终是 `com.heytap.speechassist`。
- 默认第三方服务是 `http://47.111.184.220:5000/`。
- 普通日志不得记录完整播报文本、令牌、Cookie 或完整 WebSocket 载荷。
- Profile、方法或宿主探测结果不唯一时停用对应能力。
- 原播放器未通过静态与实机验证时必须使用模块 AudioTrack；调试开关可强制 AudioTrack。
- 保留现有未提交的入口生成与延迟 Context 安装改动，禁止回退或覆盖。
- 每项生产行为先写失败测试并确认 RED，再实现最小修复。

---

### Task 1: 固化现有 Vector/YukiHook 入口修复

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/assets/xposed_init`
- Modify: `app/src/main/java/dev/breenottshook/hook/HookEntry.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/DeferredInstaller.kt`
- Test: `app/src/test/java/dev/breenottshook/hook/DeferredInstallerTest.kt`
- Test: `app/src/test/java/dev/breenottshook/hook/XposedEntryCompatibilityTest.kt`

**Interfaces:**
- `DeferredInstaller<T>.start(current: T?, defer: (((T) -> Unit) -> Unit))` 最多调用一次安装函数。
- `xposed_init` 指向 YukiHook 注解处理器实际生成且实现 `IXposedHookLoadPackage` 的类。

- [ ] **Step 1: 审阅已有脏改动并确认范围**

运行 `git diff -- app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml app/src/main/assets/xposed_init app/src/main/java/dev/breenottshook/hook/HookEntry.kt app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`。确认只包含 KSP/注解入口、延迟 Context 安装和诊断；其它修改拆到后续任务。

- [ ] **Step 2: 运行入口与延迟安装测试，确认当前状态**

运行 `./gradlew.bat :app:testDebugUnitTest --tests "*.DeferredInstallerTest" --tests "*.XposedEntryCompatibilityTest"`。若失败，记录准确失败；不得先改代码。

- [ ] **Step 3: 以最小改动修正失败**

确保 `@InjectYukiHookWithXposed` 生成入口、`xposed_init` 资源和构建插件版本一致；`BreenoHooker` 在 `appContext == null` 时订阅 Application `onCreate`，重复回调不重复安装。

- [ ] **Step 4: 验证 GREEN 与打包资源**

运行目标测试和 `./gradlew.bat :app:assembleDebug`，再用 `7z` 读取 APK 的 `assets/xposed_init`，确认生成类存在于 DEX。

- [ ] **Step 5: 单独提交入口修复**

提交信息：`fix: load module through Vector-compatible Yuki entry`。

---

### Task 2: 建立双版本 Profile 与描述符驱动安装

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/hook/VersionProfile.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/Breeno1183Profile.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/Breeno1299Profile.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/TransportDescriptor.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`
- Test: `app/src/test/java/dev/breenottshook/hook/ProfileSelectorTest.kt`
- Create: `app/src/test/java/dev/breenottshook/hook/TransportDescriptorTest.kt`

**Interfaces:**
- `VersionProfile.transport: TransportDescriptor`。
- `TransportDescriptor(className, sendMethod, cancelMethod, closeMethod)` 描述精确 JVM 方法形状。
- `ProfileSelector.select(version, ClassProbe)` 只选择一个精确版本且结构存在的 Profile。

- [ ] **Step 1: 写双版本失败测试**

增加测试：`11.8.3` 命中 `Breeno1183Profile`；`12.9.9` 命中 `Breeno1299Profile`；`12.9.10` 即使类存在也不命中；两个 Profile 都声明 `okhttp3.internal.ws.RealWebSocket.send(String): Boolean`；安装器使用 `selection.profile.transport.className`，而不是引用 11.8.3 常量。

- [ ] **Step 2: 运行并确认 RED**

运行 `./gradlew.bat :app:testDebugUnitTest --tests "*.ProfileSelectorTest" --tests "*.TransportDescriptorTest"`。预期因 `Breeno1299Profile` 和描述符接口不存在而失败。

- [ ] **Step 3: 实现最小双版本模型**

为两个版本声明独立 Profile；将传输类和方法形状移入描述符；`BreenoHooker.installTransportFallback` 只消费所选描述符。保留两个 Profile 的独立 capability/reason 文本。

- [ ] **Step 4: 验证静态 APK 证据**

对保存的 11.8.3 和 12.9.9 APK 使用 `aapt2 dump badging` 和 `dexdump`，记录版本及 `send(String)Z`、`cancel()V`、`close(ILjava/lang/String;)Z`。证据写入 `docs/COMPATIBILITY.md`，不记录用户数据。

- [ ] **Step 5: 运行 GREEN 并提交**

运行目标测试和全部单元测试。提交信息：`feat: add guarded Breeno 12.9.9 profile`。

---

### Task 3: 把传输 Hook 收紧到 TTS 请求

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/hook/TtsPayloadExtractor.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoTransportRuntime.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`
- Test: `app/src/test/java/dev/breenottshook/hook/TtsPayloadExtractorTest.kt`
- Modify: `app/src/test/java/dev/breenottshook/hook/TransportFallbackPolicyTest.kt`

**Interfaces:**
- `TtsPayloadExtractor.extract(payload: String): ExtractedTtsRequest?` 对非 TTS、空文本和未知 JSON 返回 `null`。
- `BreenoTransportRuntime.onSend(...)` 只有确认是 TTS 且配置启用时返回拦截结果。

- [ ] **Step 1: 写协议门控失败测试**

覆盖两个 APK 中观察到的 TTS 字段形状、非 TTS JSON、无效 JSON、空文本、超长文本、第三方关闭和配置无效。断言所有非目标载荷都调用原 `send`，诊断只包含长度/哈希前缀而非原文。

- [ ] **Step 2: 运行并确认 RED**

运行 `./gradlew.bat :app:testDebugUnitTest --tests "*.TtsPayloadExtractorTest" --tests "*.TransportFallbackPolicyTest"`。

- [ ] **Step 3: 实现最小协议识别与脱敏诊断**

解析已验证字段组合；不依赖单一 `text` 键即宣告 TTS。保留原 Method 和实例，只有会话协调器接受请求后才抑制当前发送。

- [ ] **Step 4: 验证回退边界**

增加播放前网络失败恢复一次原发送、开始播放后失败不恢复、`cancel/close` 只取消当前 generation 的测试。

- [ ] **Step 5: 运行 GREEN 并提交**

运行目标测试及全部单元测试。提交信息：`fix: gate transport interception to verified TTS payloads`。

---

### Task 4: 修复试听完成后 UI 不复位

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/ui/SettingsViewModel.kt`
- Modify: `app/src/main/java/dev/breenottshook/ui/SettingsDependencies.kt`
- Test: `app/src/test/java/dev/breenottshook/ui/SettingsViewModelTest.kt`
- Create: `app/src/test/java/dev/breenottshook/ui/SessionPreviewControllerTest.kt`

**Interfaces:**
- `PreviewController.preview(text, config, listener): Result<Unit>` 注册 `onStarted/onCompleted/onError/onCancelled`。
- `SettingsUiState.isPreviewing` 由真实会话回调驱动，不由 `submit()` 返回值冒充完成状态。

- [ ] **Step 1: 写失败测试**

使用可控 Fake PreviewController，断言 started 后按钮显示“停止”，completed/error/cancelled 后自动复位；旧试听的迟到回调不能覆盖新试听状态。

- [ ] **Step 2: 运行并确认 RED**

运行 `./gradlew.bat :app:testDebugUnitTest --tests "*.SettingsViewModelTest" --tests "*.SessionPreviewControllerTest"`。

- [ ] **Step 3: 实现回调桥**

将协调器的四个 `TtsCallbacks` 转发给 ViewModel；用 preview generation 忽略旧回调；错误回调设置脱敏消息，完成和取消只复位状态。

- [ ] **Step 4: 运行 GREEN 并提交**

运行目标测试及全部单元测试。提交信息：`fix: reset preview state from playback callbacks`。

---

### Task 5: 验证并接入双版本设置宿主

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/hook/SettingsHostSelector.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoSettingsHook.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/HookEntry.kt`
- Test: `app/src/test/java/dev/breenottshook/hook/SettingsHostSelectorTest.kt`
- Test: `app/src/test/java/dev/breenottshook/ui/SettingsSchemaTest.kt`
- Test: `app/src/test/java/dev/breenottshook/ui/host/HostFieldFactoryTest.kt`

**Interfaces:**
- 每个 `VersionProfile.settingsHosts` 返回该版本经验证的宿主描述符。
- `BreenoSettingsHook` 也使用延迟 Context 安装。
- `HostSettingsDialog` 的字段键集合必须与模块 APP `SettingsSchema` 完全相等。

- [ ] **Step 1: 从两个 APK 枚举设置组件**

用 `aapt2 dump xmltree` 获取 manifest Activity；用 DEX/JADX 静态检查候选设置 Activity/Fragment 的 `onCreate`、父类和页面用途。只有唯一且语义为小布设置页的宿主才能写入描述符。

- [ ] **Step 2: 写失败测试**

为确认的 11.8.3/12.9.9 宿主增加选择测试；错误版本、类缺失和多个候选返回 unavailable/ambiguous；断言宿主编辑器覆盖全部 schema key。

- [ ] **Step 3: 运行并确认 RED**

运行 `./gradlew.bat :app:testDebugUnitTest --tests "*.SettingsHostSelectorTest" --tests "*.SettingsSchemaTest" --tests "*.HostFieldFactoryTest"`。

- [ ] **Step 4: 实现 Profile 驱动注入**

设置 Hook 从所选 Profile 读取描述符，Application Context 就绪后安装。注入按钮只出现一次，打开现有完整 `HostSettingsDialog`；宿主失配只发布 `settings_disabled`。

- [ ] **Step 5: 设备验证并提交**

在 12.9.9 打开小布设置页，确认入口位置、全部字段、保存、角色刷新和模块 APP 同步。若静态分析无法证明安全宿主，保持空描述符并在兼容文档记录安全降级，不得猜测。提交信息：`feat: inject shared settings for verified Breeno versions`。

---

### Task 6: 建立原播放器能力路由与调试开关

**Files:**
- Create: `app/src/main/java/dev/breenottshook/playback/OriginalPlayerDescriptor.kt`
- Modify: `app/src/main/java/dev/breenottshook/playback/BreenoPlayerAdapter.kt`
- Modify: `app/src/main/java/dev/breenottshook/playback/CompositeAudioSink.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/VersionProfile.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`
- Create: `app/src/test/java/dev/breenottshook/playback/PlaybackRouterTest.kt`
- Modify: `app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt`

**Interfaces:**
- `PlaybackRouter.create(profile, config): AudioSink` 在强制模块播放器、描述符缺失或探测失败时返回 `AudioTrackSink`。
- `OriginalPlayerDescriptor` 包含输入格式、开始/写入/完成/停止和回调方法形状。

- [ ] **Step 1: 写路由失败测试**

覆盖：强制模块播放器；Profile 无描述符；方法多义；原播放器 `open` 失败后降级；原播放器已开始写入后失败不重放原 TTS；cancel 只停止活动 Sink。

- [ ] **Step 2: 运行并确认 RED**

运行 `./gradlew.bat :app:testDebugUnitTest --tests "*.PlaybackRouterTest" --tests "*.TtsSessionCoordinatorTest"`。

- [ ] **Step 3: 静态确认播放器候选**

从两个 APK 反向追踪 TTS WebSocket listener 到音频解码/播放器，记录候选的参数、线程、音频焦点和回调。只有完整链路唯一时创建描述符。

- [ ] **Step 4: 实现最小路由和适配器**

共享协调器只依赖 `AudioSink`；反射调用封装在 Breeno adapter；任何结构或调用异常转为 capability failure，并切换 AudioTrack。

- [ ] **Step 5: 实机验证原播放器**

在 12.9.9 分别关闭/开启“强制模块播放器”，检查 AudioTrack usage、音频焦点、停止、连续提问和完成回调。无法证明原播放器时保持 AudioTrack 作为正式兼容路径，并准确标注 `originalPlayer=false`。

- [ ] **Step 6: 运行 GREEN 并提交**

运行目标测试和全部单元测试。提交信息：`feat: route playback through verified Breeno adapter`。

---

### Task 7: 完整自动化验证、构建与安装

**Files:**
- Modify: `docs/COMPATIBILITY.md`
- Modify: `docs/DIAGNOSTICS.md`
- Modify: `docs/INSTALL.md`
- Modify: `scripts/verify.ps1`
- Output: `outputs/BreenoTTSHook-debug.apk`

**Interfaces:**
- `scripts/verify.ps1` 固定运行单元测试、Lint 和 Debug 构建并在任一步失败时返回非零。
- 兼容表分别报告 Vector 加载、传输替换、设置注入、原播放器和 AudioTrack 降级状态。

- [ ] **Step 1: 运行完整本地验证**

运行 `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。保存测试数量、Lint 结果和 APK 路径；不得用旧构建结果代替。

- [ ] **Step 2: 安装到当前设备**

用当前 `adb devices -l` 返回的在线序列号执行 `adb install -r -g`。在 Vector UI 确认模块开启且作用域包含小布，然后强制停止并冷启动小布。

- [ ] **Step 3: 验证模块与双端配置**

确认模块 APP 冷启动、角色刷新、连接测试、试听自动复位、配置持久化；确认小布设置入口（若能力启用）读写相同 `configVersion`。

- [ ] **Step 4: 验证真实播报生命周期**

清空 logcat 后触发固定无隐私问题，确认小布 UID 连接 `47.111.184.220:5000`、第三方音频播放、无重复原播报；依次测试连续提问、播放中打断、不可达地址回退、严格模式和强制模块播放器。

- [ ] **Step 5: 处理外部阻塞**

若小布再次显示账号失效或网络被 Clash 拦截，记录准确页面/域名/错误并继续完成可独立验证的 Vector、配置和试听项目；真实问答 TTS 标为 blocked，不得宣称端到端通过。

- [ ] **Step 6: 发布验证 APK**

将本次 `app/build/outputs/apk/debug/app-debug.apk` 复制为 `outputs/BreenoTTSHook-debug.apk`，计算 SHA-256，更新兼容文档并再运行 `git diff --check`。

- [ ] **Step 7: 提交文档与交付信息**

提交信息：`docs: verify Breeno dual-version compatibility`。最终报告自动化验证、12.9.9 实机结果、11.8.3 验证层级、仍降级的能力及 APK SHA-256。
