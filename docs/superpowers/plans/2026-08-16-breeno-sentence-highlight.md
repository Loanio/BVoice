# Breeno Sentence Highlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 12.9.9 的第三方 TTS 按小布文本分片顺序播放，并在每句第三方音频实际开始写入时回灌宿主高亮回调。

**Architecture:** 在现有 `TtsSessionCoordinator` 与 `BreenoEngineRuntime` 之间增加句子级播放队列。流式入口仍先收集宿主分片；`J0` 后按分片/句末标点拆分为有序 utterance，每个 utterance 单独调用现有 `/tts`，队列只允许一个会话播放。每个 utterance 首次 PCM 写入时触发宿主 stream listener 的分片开始回调，完成后进入下一句；取消、失败和新代际淘汰保证 exactly-once。

**Tech Stack:** Kotlin, coroutines, OkHttp/GPT-SoVITS client, AudioTrack, YukiHookAPI reflection hooks, JUnit coroutine tests.

## Global Constraints

- 只 Hook 已验证的 12.9.9 `TTSEngineImpl` 业务入口，不 Hook 全局 SDK。
- 保留现有 `/tts` API 字段、配置、WAV 解码和 `AudioTrackSink`。
- 日志只记录分片序号、字符数、状态和摘要，不记录正文、凭据或完整 Bundle。
- 第三方 PCM 开始前允许原 TTS 回退；PCM 开始后不得双播原句。
- 取消、新会话和异常必须最多产生一个终态，旧会话回调不能影响新会话。

### Task 1: 定义句子队列与分片回调契约

**Files:**
- Create: `app/src/main/java/dev/breenottshook/session/TtsUtterance.kt`
- Modify: `app/src/main/java/dev/breenottshook/session/TtsInvocation.kt`
- Test: `app/src/test/java/dev/breenottshook/session/TtsUtteranceTest.kt`

**Interfaces:**
- Produces `data class TtsUtterance(val index: Int, val text: String)` and `fun splitUtterances(chunks: List<String>): List<TtsUtterance>`.
- `TtsInvocation` carries optional utterance index and stream callback context without changing existing normal-speak callers.

- [ ] Write failing tests for Chinese punctuation boundaries, newline boundaries, retained punctuation, empty chunks, and fallback order.
- [ ] Run `testDebugUnitTest --tests dev.breenottshook.session.TtsUtteranceTest`; confirm failure because the splitter/metadata does not exist.
- [ ] Implement the smallest splitter that preserves source order and never emits blank utterances.
- [ ] Run the focused test and confirm PASS.

### Task 2: Add sequential utterance playback and progress callbacks

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/session/TtsSessionCoordinator.kt`
- Modify: `app/src/main/java/dev/breenottshook/session/TtsSessionState.kt`
- Test: `app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt`

**Interfaces:**
- `submitStream(utterances: List<TtsUtterance>, callbacks: TtsCallbacks, originalCall: OriginalCall)` starts one generation and serializes synthesis/playback.
- `TtsCallbacks.onUtteranceStarted(index: Int)` is invoked immediately after the first playable PCM write for that utterance.

- [ ] Write failing tests for ordered synthesis, one start callback per utterance, completion only after the final utterance, cancellation between utterances, pre-PCM fallback, post-PCM no-fallback, and superseded generations.
- [ ] Run the focused coordinator tests and verify expected failures.
- [ ] Implement a queue loop that creates a fresh decoder/sink segment per utterance, reuses current config/client/sink policy, and guards callbacks by generation and terminal state.
- [ ] Run focused tests and then all session tests.

### Task 3: Bridge host listener highlighting from engine runtime

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoHostCallbacks.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoEngineRuntime.kt`
- Test: `app/src/test/java/dev/breenottshook/hook/BreenoHostCallbacksTest.kt`
- Test: `app/src/test/java/dev/breenottshook/hook/BreenoEngineRuntimeTest.kt`

**Interfaces:**
- Stream callbacks expose `onUtteranceStarted(index)` through the verified listener method shape; missing methods remain a safe no-op with diagnostics.
- `BreenoEngineRuntime` passes split utterances to the coordinator for both normal `G/O0/J0` streams and implicit “播报全文” streams without `G`.

- [ ] Write failing reflection tests for the verified start method, argument shape, missing-listener safety, and exactly-once index order.
- [ ] Run focused hook tests and confirm failure.
- [ ] Implement the callback bridge and route both explicit and implicit streams through `submitStream`.
- [ ] Run all hook tests and verify old 11.8.3 WebSocket behavior is unchanged.

### Task 4: Device regression and documentation

**Files:**
- Modify: `docs/COMPATIBILITY.md`
- Modify: `docs/DIAGNOSTICS.md`
- Modify: `docs/INSTALL.md`

- [ ] Run full `testDebugUnitTest` and `assembleDebug`.
- [ ] Install the APK on `192.168.0.102:6666`, reload Vector scope, and cold-start `com.heytap.speechassist`.
- [ ] Verify logs for utterance indices, `/tts` request order, first-PCM callback, completion, cancellation, and fallback.
- [ ] Manually confirm “播报全文” changes the host-highlighted sentence as each third-party utterance begins.
- [ ] Record the verified listener method and device result in the compatibility/diagnostics/install docs.
