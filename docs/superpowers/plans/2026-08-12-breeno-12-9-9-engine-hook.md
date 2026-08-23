# Breeno 12.9.9 Engine Hook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every verified Breeno 12.9.9 `TTSEngineImpl` utterance with audio from the existing configurable GPT-SoVITS API while retaining the 11.8.3 WebSocket implementation.

**Architecture:** Version profiles select either the existing 11.8.3 WebSocket installer or a new 12.9.9 engine installer. A platform-independent stream accumulator converts `P0 → O0* → J0` into one `TtsInvocation`; a reflective host bridge maps completion and interruption back to the verified Breeno listener interfaces. Both routes reuse `GptSovitsClient`, `TtsSessionCoordinator`, `AudioTrackSink`, shared ContentProvider configuration, and playback-before/after fallback rules.

**Tech Stack:** Kotlin, Android 15, YukiHookAPI, Kotlin coroutines, OkHttp, kotlinx.serialization, JUnit 4, Robolectric, Gradle 8.11.1/JDK 17, ADB, JADX MCP.

## Global Constraints

- Support only exact Breeno versions `11.8.3` and `12.9.9`; future versions remain unsupported until reverified.
- Keep `11.8.3` on the existing guarded `RealWebSocket` route.
- Use the GPT-SoVITS root URL entered by the user.
- Preserve `GET /character_list` and `POST /tts` with every existing JSON request field unchanged.
- Do not hook every call to the global SDK `com.heytap.speechassist.sdk.TTSEngine`.
- Do not use an unverified Breeno original-player injection point; use `AudioTrackSink` for 12.9.9.
- Permit original TTS fallback only before third-party PCM starts and when `fallbackToOriginal=true` and `strictMode=false`.
- Never log full utterance text, tokens, cookies, full payloads, or sensitive Bundle values.
- Preserve the user's existing uncommitted `BreenoTransportRuntime.kt` status logging and the tested DEBUG WebSocket diagnostic changes.
- Use JDK 17 via `JAVA_HOME=C:\Program Files\Java\jdk-17` for Gradle commands.

---

### Task 1: Model Version-Specific TTS Routes and Verified 12.9.9 Descriptors

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/hook/VersionProfile.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/Breeno1183Profile.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/Breeno1299Profile.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/EngineTtsDescriptor.kt`
- Modify: `app/src/test/java/dev/breenottshook/hook/TransportDescriptorTest.kt`
- Modify: `app/src/test/java/dev/breenottshook/hook/ProfileSelectorTest.kt`

**Interfaces:**
- Produces: `sealed interface TtsRoute`, `TtsRoute.WebSocket(TransportDescriptor)`, `TtsRoute.Engine(EngineTtsDescriptor)`.
- Produces: `EngineTtsDescriptor(className, speak, streamStart, streamChunk, streamEnd, streamCancel, shutup, streamPause, streamResume)`.
- Consumes: existing `MethodDescriptor` and `ClassProbe`.

- [ ] **Step 1: Write failing descriptor and profile tests**

Assert these exact 12.9.9 observations:

```kotlin
val route = assertIs<TtsRoute.Engine>(Breeno1299Profile().ttsRoute)
assertEquals("com.heytap.speechassist.core.engine.TTSEngineImpl", route.descriptor.className)
assertEquals(
    MethodDescriptor(
        "m39754C0",
        listOf("java.lang.String", "km.w", "android.os.Bundle",
            "com.heytap.speechassist.sdk.TTSEngine\$SlpTtsCallBack"),
        "void"
    ),
    route.descriptor.speak
)
assertEquals(MethodDescriptor("m39779P0", listOf(
    "com.heytap.speechassist.sdk.tts.StreamTtsListener", "android.os.Bundle"), "void"),
    route.descriptor.streamStart)
assertEquals(MethodDescriptor("m39777O0", listOf("java.lang.String"), "void"),
    route.descriptor.streamChunk)
assertEquals(MethodDescriptor("m39768J0", emptyList(), "void"), route.descriptor.streamEnd)
assertIs<TtsRoute.WebSocket>(Breeno1183Profile().ttsRoute)
```

Change the 12.9.9 profile selection probe to require `TTSEngineImpl`, and assert its capabilities have `businessTtsEntry=true` and `transportFallback=false`.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
& .\gradlew.bat testDebugUnitTest --tests dev.breenottshook.hook.TransportDescriptorTest --tests dev.breenottshook.hook.ProfileSelectorTest
```

Expected: compilation failure because `TtsRoute`, `EngineTtsDescriptor`, and `ttsRoute` do not exist.

- [ ] **Step 3: Implement the minimal route model and descriptors**

Make `VersionProfile` expose `val ttsRoute: TtsRoute` instead of an unconditional transport. Keep a convenience transport only inside `TtsRoute.WebSocket`. Encode the JADX-observed method names and parameter types in `Breeno1299Profile`; encode cancellation methods from `TTSEngineImpl`/SDK only after checking each with JADX MCP during this step. If a stop method belongs to the SDK rather than `TTSEngineImpl`, omit it from this descriptor and handle the verified `TTSEngineImpl` wrapper only.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/hook/VersionProfile.kt app/src/main/java/dev/breenottshook/hook/EngineTtsDescriptor.kt app/src/main/java/dev/breenottshook/hook/Breeno1183Profile.kt app/src/main/java/dev/breenottshook/hook/Breeno1299Profile.kt app/src/test/java/dev/breenottshook/hook/TransportDescriptorTest.kt app/src/test/java/dev/breenottshook/hook/ProfileSelectorTest.kt
git commit -m "feat: model Breeno 12.9.9 engine route"
```

### Task 2: Build a Deterministic Stream Utterance Accumulator

**Files:**
- Create: `app/src/main/java/dev/breenottshook/hook/StreamUtteranceAccumulator.kt`
- Create: `app/src/test/java/dev/breenottshook/hook/StreamUtteranceAccumulatorTest.kt`

**Interfaces:**
- Produces: `StreamUtteranceAccumulator(maxChars: Int = 100_000)`.
- Produces: `start(listener: Any?, bundle: Any?): StreamFallback?`, `append(text: String): AppendResult`, `finish(): FinishedStream`, `cancel(): StreamFallback?`.
- Produces immutable `StreamFallback(listener, bundle, chunks)` for replay and `FinishedStream.Empty`, `FinishedStream.Ready(text, fallback)`, `FinishedStream.Overflow(fallback)`.

- [ ] **Step 1: Write failing accumulator tests**

Cover these literal behaviors:

```kotlin
accumulator.start(listener, bundle)
assertEquals(AppendResult.Accepted, accumulator.append("第一段"))
assertEquals(AppendResult.Accepted, accumulator.append("第二段"))
val ready = assertIs<FinishedStream.Ready>(accumulator.finish())
assertEquals("第一段第二段", ready.text)
assertEquals(listOf("第一段", "第二段"), ready.fallback.chunks)
```

Add separate tests for blank chunks being ignored, empty finish, overflow retaining exact original chunks, cancel returning the current fallback once, and a second `start` returning the superseded fallback before resetting state.

- [ ] **Step 2: Run the test and verify RED**

Run `testDebugUnitTest --tests dev.breenottshook.hook.StreamUtteranceAccumulatorTest`. Expected: compilation failure for the missing class.

- [ ] **Step 3: Implement a synchronized minimal state machine**

Use an internal immutable snapshot plus `synchronized` methods. Count Kotlin `String.length` consistently with existing diagnostics. Never store logs or hashes in this class.

- [ ] **Step 4: Run the focused test and verify GREEN**

Expected: all accumulator cases PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/hook/StreamUtteranceAccumulator.kt app/src/test/java/dev/breenottshook/hook/StreamUtteranceAccumulatorTest.kt
git commit -m "feat: accumulate Breeno stream utterances"
```

### Task 3: Implement Reflection-Safe Host Callback Bridges

**Files:**
- Create: `app/src/main/java/dev/breenottshook/hook/BreenoHostCallbacks.kt`
- Create: `app/src/test/java/dev/breenottshook/hook/BreenoHostCallbacksTest.kt`

**Interfaces:**
- Produces: `BreenoHostCallbacks.normal(listener: Any?): TtsCallbacks`.
- Produces: `BreenoHostCallbacks.stream(listener: Any?): TtsCallbacks`.
- Normal mapping: start → `onSpeakStart()`, complete → `onSpeakCompleted()`, error → `onTtsError(MODULE_ERROR_CODE, errorClass)`, cancel → `onSpeakInterrupted(MODULE_INTERRUPTED_REASON)`.
- Stream mapping: start → `onSpeakBegin()`, complete → `onEnd()` then `onCompleted(null)`, error/cancel → `onCompleted(SpeechException)` only if a verified safe constructor is available; otherwise call `onEnd()` and publish diagnostic without fabricating an exception.

- [ ] **Step 1: Verify the SpeechException constructor with JADX MCP**

Call `get_methods_of_class` for `com.heytap.voiceassistant.sdk.tts.SpeechException` and record the exact usable constructor in a comment beside the bridge. If no safe public constructor exists, select the explicitly allowed `onEnd()`-only error path.

- [ ] **Step 2: Write failing tests using real fake listener classes**

Create local test fakes exposing the exact verified method names. Assert ordered event lists such as `listOf("start", "complete")`, exactly one terminal callback, null listeners doing nothing, and missing methods never throwing into the coordinator.

- [ ] **Step 3: Run the test and verify RED**

Expected: compilation failure for missing `BreenoHostCallbacks`.

- [ ] **Step 4: Implement cached method resolution**

Resolve by exact name and exact primitive/reference parameter shape; cache per listener class in `ConcurrentHashMap`. Invocation failures must be reported through an injected `(String, Throwable?) -> Unit` diagnostic callback and must not crash the host process.

- [ ] **Step 5: Run the focused test and verify GREEN**

Expected: callback order, terminal idempotence, and missing-method safety PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/hook/BreenoHostCallbacks.kt app/src/test/java/dev/breenottshook/hook/BreenoHostCallbacksTest.kt
git commit -m "feat: bridge third-party TTS host callbacks"
```

### Task 4: Add the 12.9.9 Engine Runtime

**Files:**
- Create: `app/src/main/java/dev/breenottshook/hook/BreenoEngineRuntime.kt`
- Create: `app/src/test/java/dev/breenottshook/hook/BreenoEngineRuntimeTest.kt`
- Modify: `app/src/main/java/dev/breenottshook/session/TtsSessionCoordinator.kt`
- Modify: `app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `HookConfigCache`, `TtsSessionCoordinator`, `StreamUtteranceAccumulator`, `BreenoHostCallbacks`.
- Produces: `onSpeak(text, listener, original): Boolean`, `onStreamStart(listener, bundle, original): Boolean`, `onStreamChunk(text, original): Boolean`, `onStreamEnd(original): Boolean`, `cancel(reason)`.
- Boolean return means `true` when the original host method must be suppressed.

- [ ] **Step 1: Write failing runtime tests**

Use injected `submit: suspend (TtsInvocation) -> Unit` and `cancel: suspend (String) -> Unit` functions so tests do not require Android audio. Cover:

- disabled config returns `false` for every method;
- normal speak submits exactly one invocation and returns `true`;
- `P0`, two chunks, and `J0` submit one concatenated utterance;
- empty stream does not submit;
- new stream and cancel discard old buffered chunks;
- original fallback closure replays `start`, each chunk in order, then `end` exactly once;
- overflow uses the same ordered replay closure;
- diagnostics contain only character counts/hash and never test text.

- [ ] **Step 2: Run runtime tests and verify RED**

Expected: compilation failure for missing runtime.

- [ ] **Step 3: Make coordinator report fallback execution**

Change `OriginalCall.resume()` to return `Unit` as today, but add an optional callback after successful fallback so the runtime can publish `fallback`. Add a regression test proving the original closure remains exactly-once under an error/cancel race. Do not change playback-before/after policy.

- [ ] **Step 4: Implement the runtime**

Construct the same `GptSovitsEngine(GptSovitsClient(OkHttpClient()))` and `AudioTrackSink(context.applicationContext)` used by `BreenoTransportRuntime`. Refresh config at every public entry. For stream finish, create one `OriginalCall` that invokes saved original start, each saved original chunk, then original end. Publish `intercepted`, `playing`, `completed`, `failed`, `cancelled`, or `fallback` using hashes/counts only.

- [ ] **Step 5: Run runtime and coordinator tests and verify GREEN**

Run both focused test classes. Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/hook/BreenoEngineRuntime.kt app/src/test/java/dev/breenottshook/hook/BreenoEngineRuntimeTest.kt app/src/main/java/dev/breenottshook/session/TtsSessionCoordinator.kt app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt
git commit -m "feat: run third-party TTS from Breeno engine calls"
```

### Task 5: Install the Correct Route Through YukiHookAPI

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/EngineTtsInstaller.kt`
- Create: `app/src/test/java/dev/breenottshook/hook/EngineTtsInstallerTest.kt`
- Modify: `app/src/test/java/dev/breenottshook/hook/TransportDescriptorTest.kt`

**Interfaces:**
- Consumes: selected `VersionProfile.ttsRoute`.
- Produces: `EngineTtsInstaller.resolve(clazz, descriptor): EngineMethods` which rejects zero or multiple matches before hooks are installed.
- Produces status detail `profile=breeno-12.9.9;engine=true;transport=false;originalPlayer=false`.

- [ ] **Step 1: Write failing resolver tests**

Create a fixture class with exact method shapes and assert all required methods resolve once. Create missing and overloaded fixtures and assert `EngineInstallResult.Disabled(reason)` rather than partial installation.

- [ ] **Step 2: Run tests and verify RED**

Expected: missing resolver types.

- [ ] **Step 3: Implement route dispatch and hooks**

In `BreenoHooker`, dispatch `TtsRoute.WebSocket` to the existing installer and `TtsRoute.Engine` to `EngineTtsInstaller`. Hook before each resolved method:

- `C0`: capture text/listener and an exact original reflective call; suppress when runtime returns true.
- `P0`: capture listener/Bundle and original call.
- `O0`: capture each text chunk and original call.
- `J0`: capture original end call.
- verified cancel/shutup wrappers: cancel the runtime before allowing or suppressing as defined by descriptor tests.

Reuse the existing host-gated cleartext policy for both routes. Do not hook SDK-global `TTSEngine` methods.

- [ ] **Step 4: Run resolver, profile, and entry tests and verify GREEN**

Run `EngineTtsInstallerTest`, `TransportDescriptorTest`, `ProfileSelectorTest`, and `XposedEntryCompatibilityTest`. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt app/src/main/java/dev/breenottshook/hook/EngineTtsInstaller.kt app/src/test/java/dev/breenottshook/hook/EngineTtsInstallerTest.kt app/src/test/java/dev/breenottshook/hook/TransportDescriptorTest.kt
git commit -m "feat: install version-specific Breeno TTS hooks"
```

### Task 6: Preserve the User API Contract and Finish Diagnostics

**Files:**
- Modify: `app/src/test/java/dev/breenottshook/api/GptSovitsClientTest.kt`
- Modify: `app/src/main/java/dev/breenottshook/hook/HookDiagnostics.kt`
- Modify: `app/src/test/java/dev/breenottshook/hook/TtsPayloadExtractorTest.kt`
- Modify: `docs/DIAGNOSTICS.md`
- Modify: `docs/COMPATIBILITY.md`

**Interfaces:**
- Confirms unchanged `GET /character_list` and `POST /tts` contracts.
- Produces safe `HookDiagnostics.stream(chunkCount, totalChars, digestSource)`.

- [ ] **Step 1: Add failing API and diagnostic regression assertions**

Use MockWebServer to assert `/tts`, POST, and the exact existing JSON keys. Assert `/character_list` remains GET. Add tests proving WebSocket and engine diagnostics omit query strings, utterance content, character names, and API response bodies.

- [ ] **Step 2: Run focused tests and verify the intended failure**

The new stream diagnostic assertion must fail because the helper is absent; the API contract assertions should pass unchanged.

- [ ] **Step 3: Implement only the safe diagnostic helper and update docs**

Document 11.8.3 as transport-hooked and 12.9.9 as engine-hooked. Document that updating the module APK requires toggling it off/on in Vector and cold-starting Breeno.

- [ ] **Step 4: Run focused tests and verify GREEN**

Expected: API and privacy tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/test/java/dev/breenottshook/api/GptSovitsClientTest.kt app/src/main/java/dev/breenottshook/hook/HookDiagnostics.kt app/src/test/java/dev/breenottshook/hook/TtsPayloadExtractorTest.kt docs/DIAGNOSTICS.md docs/COMPATIBILITY.md
git commit -m "test: lock GPT-SoVITS and diagnostic contracts"
```

### Task 7: Full Verification, APK Installation, and Device Acceptance

**Files:**
- Verify: all project sources
- Artifact: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Requires: device `100.94.241.58:6666`, Vector `org.matrix.vector.manager`, Breeno `com.heytap.speechassist` 12.9.9.
- Produces: verified APK SHA-256 and an evidence-backed runtime result.

- [ ] **Step 1: Run the complete verification suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
& .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`; generated Yuki warnings may remain, but no test/lint/build failures.

- [ ] **Step 2: Inspect worktree scope**

Run `git status --short`, `git diff --check`, and review every remaining diff. Preserve unrelated/user changes; commit the diagnostic probe separately if it is now covered and intentional.

- [ ] **Step 3: Install APK and reload Vector through UI**

Install with `adb install -r`. Open Vector UI, toggle BreenoTTSHook off then on, leave `com.heytap.speechassist` in scope, force-stop Breeno, and cold-start it. Do not edit Vector databases.

- [ ] **Step 4: Verify hook installation**

Clear logcat and enter the Breeno settings page normally. Require both the injected “第三方音色” entry and:

```text
state=active;detail=profile=breeno-12.9.9;engine=true;transport=false
state=settings_active
```

- [ ] **Step 5: Verify the configured API before end-to-end playback**

From the module APP, run “测试连接”, refresh characters, select a valid role/emotion, save, and run preview. Require a successful request to the configured service; do not log test text or the catalog response.

- [ ] **Step 6: Verify normal and stream utterances**

Trigger one Breeno native short reply and one AI streaming reply with speaker output enabled. Require `intercepted → playing → completed`, an HTTP request to the configured `/tts`, audible third-party voice, and no original double playback.

- [ ] **Step 7: Verify interruption and fallback**

During playback submit a second request and verify the first is cancelled. Temporarily set an unreachable API port and verify playback-before-start fallback uses original TTS; enable strict mode and verify the same failure reports `failed` without silent fallback. Restore the valid URL afterward.

- [ ] **Step 8: Record limitations and final hash**

Compute `Get-FileHash -Algorithm SHA256 app-debug.apk`. If 11.8.3 cannot be activated on the device, state that only automated/structural regression was completed for it. Do not claim end-to-end success unless third-party audio is actually heard and the expected logs appear.

- [ ] **Step 9: Final commit if verification created intentional tracked changes**

```powershell
git add <only intentionally verified files>
git commit -m "fix: adapt Breeno 12.9.9 TTS engine"
```
