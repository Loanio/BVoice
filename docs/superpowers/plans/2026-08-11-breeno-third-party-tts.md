# BreenoTTSHook Third-Party TTS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable Yuki Hook API/LSPosed module that replaces Breeno 11.8.3 TTS with the configured GPT-SoVITS service, prefers Breeno's original player, provides a safe AudioTrack fallback, and exposes one shared configuration through both the module app and the injected Breeno settings UI.

**Architecture:** A single Android application module contains small, isolated packages for configuration, API transport, streaming WAV decoding, playback, session coordination, Yuki hooks, and UI. A UID-restricted exported `ContentProvider` is the sole persistent configuration source; the hooked process holds an immutable cached snapshot. Exact 11.8.3 hook descriptors are preferred, while a URL-gated OkHttp fallback supplies a working module-player path when the business/player symbols cannot be verified.

**Tech Stack:** Gradle 8.11.1, Android Gradle Plugin 8.9.1, JDK 17, Kotlin 2.1.10, compile/target SDK 35, min SDK 31, Yuki Hook API 1.2.1, KSP, Jetpack Compose Material 3, OkHttp 4.12.0, kotlinx-coroutines, kotlinx-serialization, JUnit 4, MockWebServer, Robolectric.

## Global Constraints

- Target package: `com.heytap.speechassist`; stable target version: 11.8.3.
- Target OS: Android 15 / ColorOS 15; do not use world-readable preferences.
- The API base URL is entered by the user; warn that HTTP is unencrypted.
- Replace every utterance entering the selected Breeno TTS component.
- Prefer the verified Breeno player, then fall back to module AudioTrack; never guess an unsafe player method.
- Keep a user-controlled original-TTS fallback switch and a strict third-party debug mode.
- Both settings surfaces expose every field and write the same versioned configuration.
- Character/emotion values load from `/character_list`, retain a cache, and allow manual values.
- Never persist full utterance text in ordinary logs.
- Production behavior must be introduced test-first; Gradle/configuration scaffolding and generated wrapper files are exempt.

---

## File Map

- Root Gradle files own versions, repositories, wrapper, and module inclusion.
- `app/src/main/java/dev/breenottshook/config/` owns immutable configuration, validation, IPC contract, storage, and hooked-process cache.
- `app/src/main/java/dev/breenottshook/api/` owns GPT-SoVITS request/response behavior and character caching.
- `app/src/main/java/dev/breenottshook/audio/` owns WAV parsing and normalized PCM types.
- `app/src/main/java/dev/breenottshook/session/` owns request generations, state transitions, cancellation, callbacks, and fallback decisions.
- `app/src/main/java/dev/breenottshook/playback/` owns Breeno-player capability adapters and AudioTrack fallback.
- `app/src/main/java/dev/breenottshook/hook/` owns Yuki entry points, version profiles, intercepted original calls, settings injection, and the URL-gated transport fallback.
- `app/src/main/java/dev/breenottshook/ui/` owns the shared schema, Compose module UI, and host-native injected editor.
- `app/src/test/` owns JVM behavior tests; `app/src/androidTest/` owns provider and UI integration tests.

---

### Task 1: Reproducible Android and LSPosed Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/assets/xposed_init`
- Create: `app/src/main/res/values/strings.xml`, `themes.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `.gitignore`

**Interfaces:**
- Produces application ID `dev.breenottshook`, namespace `dev.breenottshook`, launcher activity `.ui.MainActivity`, provider authority `dev.breenottshook.config`, and Xposed entry `dev.breenottshook.hook.HookEntry`.

- [ ] **Step 1: Create the Gradle catalog and build scripts**

Pin the versions in `libs.versions.toml`; declare Android application, Kotlin Android, Kotlin serialization, Compose, KSP, Yuki Hook API, Xposed compile-only API, OkHttp, coroutines, serialization, JUnit, MockWebServer, Robolectric, AndroidX test, and Compose test dependencies. Set `compileSdk = 35`, `targetSdk = 35`, `minSdk = 31`, JVM target 17, Compose enabled, and unit tests with Android resources.

- [ ] **Step 2: Generate the Gradle 8.11.1 wrapper**

Run the cached Gradle distribution with JDK 17:

```powershell
$env:JAVA_HOME='C:\Users\27623\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
& 'C:\Users\27623\.gradle\wrapper\dists\gradle-8.11.1-bin'\*\gradle-8.11.1\bin\gradle.bat wrapper --gradle-version 8.11.1
```

- [ ] **Step 3: Declare Android and LSPosed metadata**

The manifest must request `INTERNET`, set `usesCleartextTraffic="true"`, reference the scoped network security file, export `ConfigProvider`, declare the launcher activity, and include LSPosed module metadata. `xposed_init` contains exactly `dev.breenottshook.hook.HookEntry`.

- [ ] **Step 4: Verify dependency resolution and an empty debug build**

Run:

```powershell
$env:JAVA_HOME='C:\Users\27623\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
.\gradlew.bat :app:assembleDebug --stacktrace
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Commit**

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle gradle.properties gradlew gradlew.bat app
git commit -m "build: scaffold Yuki Hook Android module"
```

---

### Task 2: Immutable Configuration and Validation

**Files:**
- Create: `app/src/test/java/dev/breenottshook/config/ConfigValidatorTest.kt`
- Create: `app/src/main/java/dev/breenottshook/config/TtsConfig.kt`
- Create: `app/src/main/java/dev/breenottshook/config/ConfigValidator.kt`
- Create: `app/src/main/java/dev/breenottshook/config/ConfigCodec.kt`

**Interfaces:**
- Produces `data class TtsConfig`, `data class ConfigSnapshot(val version: Long, val value: TtsConfig)`, `sealed interface ValidationResult`, `ConfigValidator.validate(TtsConfig)`, and JSON `ConfigCodec.encode/decode`.
- Defaults include an empty base URL, WAV, streaming enabled, fallback enabled, strict mode disabled, and module-player forcing disabled.

- [ ] **Step 1: Write failing validation tests**

Test URL normalization, rejection of non-HTTP schemes, `speed > 0`, `batchSize >= 1`, `topP in 0.0..1.0`, `temperature > 0`, timeouts in `1_000..120_000`, and round-trip JSON equality.

```kotlin
@Test fun `normalizes base URL with trailing slash`() {
    val result = ConfigValidator.validate(TtsConfig(baseUrl = "http://tts.example.test:5000"))
    assertEquals("http://tts.example.test:5000/", (result as ValidationResult.Valid).value.baseUrl)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.ConfigValidatorTest"`. Expected: compilation failure because `TtsConfig` and `ConfigValidator` do not exist.

- [ ] **Step 3: Implement the minimal immutable model, validator, and codec**

Keep API fields typed and separate manual character/emotion overrides from fetched choices. Encode the complete configuration as one JSON document so provider writes are atomic.

- [ ] **Step 4: Run the test and verify GREEN**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.ConfigValidatorTest"`. Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/config app/src/test/java/dev/breenottshook/config
git commit -m "feat: add validated TTS configuration"
```

---

### Task 3: Versioned Cross-Process Configuration Provider

**Files:**
- Create: `app/src/test/java/dev/breenottshook/config/ConfigRepositoryTest.kt`
- Create: `app/src/androidTest/java/dev/breenottshook/config/ConfigProviderTest.kt`
- Create: `app/src/main/java/dev/breenottshook/config/ConfigContract.kt`
- Create: `app/src/main/java/dev/breenottshook/config/ConfigStore.kt`
- Create: `app/src/main/java/dev/breenottshook/config/SharedPrefsConfigStore.kt`
- Create: `app/src/main/java/dev/breenottshook/config/ConfigProvider.kt`
- Create: `app/src/main/java/dev/breenottshook/config/ConfigRepository.kt`
- Create: `app/src/main/java/dev/breenottshook/config/HookConfigCache.kt`

**Interfaces:**
- `ConfigStore.read(): ConfigSnapshot`
- `ConfigStore.update(expectedVersion: Long?, config: TtsConfig): UpdateResult`
- Provider `call()` methods `get_config`, `update_config`, `get_hook_status`, `put_hook_status`.
- `ConfigRepository.observe(): StateFlow<ConfigSnapshot>` and `suspend fun update(config): UpdateResult`.

- [ ] **Step 1: Write failing store and cache tests**

Test default snapshot version 0, atomic version increments, stale expected-version rejection, invalid update rejection, and `HookConfigCache` retaining the last valid snapshot when IPC reads fail.

- [ ] **Step 2: Run tests and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.ConfigRepositoryTest"`. Expected: missing production types.

- [ ] **Step 3: Implement store, contract, repository, and cache**

Use one private SharedPreferences JSON value plus version. Guard updates with a process-local lock and `commit()` before `notifyChange()`. The repository exposes immutable snapshots.

- [ ] **Step 4: Write and run provider authorization tests**

Instrumented tests verify module UID acceptance, invalid payload rejection, version conflict response, and notifications. Put caller authorization in a separate function accepting `uidPackages: Set<String>` so JVM tests verify only `dev.breenottshook` and `com.heytap.speechassist` are accepted.

- [ ] **Step 5: Run GREEN verification**

Run `./gradlew.bat :app:testDebugUnitTest`. If an emulator/device exists, also run `./gradlew.bat :app:connectedDebugAndroidTest`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/config app/src/test app/src/androidTest app/src/main/AndroidManifest.xml
git commit -m "feat: share versioned configuration across processes"
```

---

### Task 4: GPT-SoVITS API and Character Cache

**Files:**
- Create: `app/src/test/java/dev/breenottshook/api/GptSovitsClientTest.kt`
- Create: `app/src/main/java/dev/breenottshook/api/GptSovitsClient.kt`
- Create: `app/src/main/java/dev/breenottshook/api/CharacterCatalog.kt`
- Create: `app/src/main/java/dev/breenottshook/api/CharacterCache.kt`
- Create: `app/src/main/java/dev/breenottshook/api/ApiError.kt`

**Interfaces:**
- `suspend fun fetchCharacters(baseUrl: String): CharacterCatalog`
- `suspend fun synthesize(text: String, config: TtsConfig, onBytes: suspend (ByteArray) -> Unit): SynthesisResult`
- `CharacterCache.getOrFetch(baseUrl, forceRefresh): CatalogState`.

- [ ] **Step 1: Write failing MockWebServer tests**

Assert `/character_list` decoding, POST `/tts` JSON fields, streamed body forwarding, non-2xx errors, timeout mapping, response-size limit, cancellation, cached catalog fallback, and preservation of manual character/emotion values.

- [ ] **Step 2: Run tests and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.GptSovitsClientTest"`. Expected: missing client types.

- [ ] **Step 3: Implement the minimal cancellable client**

Build endpoint URLs with `HttpUrl.resolve`, use `Call.execute()` on `Dispatchers.IO`, read bounded chunks, call `ensureActive()` between reads, and cancel the OkHttp call from `invokeOnCancellation`.

- [ ] **Step 4: Run tests and verify GREEN**

Run the targeted test and then all unit tests. Expected: pass without using the public server.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/api app/src/test/java/dev/breenottshook/api
git commit -m "feat: add GPT-SoVITS API compatibility client"
```

---

### Task 5: Streaming WAV Decoder

**Files:**
- Create: `app/src/test/java/dev/breenottshook/audio/StreamingWavDecoderTest.kt`
- Create: `app/src/test/java/dev/breenottshook/audio/WavFixtures.kt`
- Create: `app/src/main/java/dev/breenottshook/audio/PcmFormat.kt`
- Create: `app/src/main/java/dev/breenottshook/audio/PcmSegment.kt`
- Create: `app/src/main/java/dev/breenottshook/audio/StreamingWavDecoder.kt`
- Create: `app/src/main/java/dev/breenottshook/audio/AudioDecodeError.kt`

**Interfaces:**
- `StreamingWavDecoder.feed(bytes: ByteArray): List<PcmSegment>`
- `StreamingWavDecoder.finish(): DecodeFinish`
- `PcmFormat(sampleRate: Int, channels: Int, bitsPerSample: Int)`.

- [ ] **Step 1: Write failing decoder tests**

Generate in-memory fixtures for a complete PCM WAV, one-byte-at-a-time input, headers split at every offset, unknown RIFF chunks, odd-byte padding, two concatenated WAV files, truncated data, invalid RIFF size, unsupported compression, and format changes between sentence WAVs.

- [ ] **Step 2: Run tests and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.StreamingWavDecoderTest"`. Expected: missing decoder.

- [ ] **Step 3: Implement a bounded RIFF state machine**

Parse little-endian sizes, locate `fmt ` and `data`, emit PCM only after format validation, reset cleanly at the next `RIFF`, and cap buffered non-audio bytes. Do not use network chunk boundaries as framing.

- [ ] **Step 4: Run tests and verify GREEN**

Run the targeted test and all unit tests.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/audio app/src/test/java/dev/breenottshook/audio
git commit -m "feat: decode streamed and concatenated WAV audio"
```

---

### Task 6: Playback Contracts and TTS Session Coordinator

**Files:**
- Create: `app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt`
- Create: `app/src/main/java/dev/breenottshook/playback/AudioSink.kt`
- Create: `app/src/main/java/dev/breenottshook/playback/AudioTrackSink.kt`
- Create: `app/src/main/java/dev/breenottshook/playback/BreenoPlayerAdapter.kt`
- Create: `app/src/main/java/dev/breenottshook/playback/CompositeAudioSink.kt`
- Create: `app/src/main/java/dev/breenottshook/session/TtsInvocation.kt`
- Create: `app/src/main/java/dev/breenottshook/session/TtsSessionState.kt`
- Create: `app/src/main/java/dev/breenottshook/session/TtsSessionCoordinator.kt`

**Interfaces:**
- `AudioSink.open(format)`, `write(segment)`, `complete()`, `cancel()`.
- `TtsInvocation(text, originalCall, callbacks)` where `OriginalCall.resume()` invokes the preserved original member once.
- `TtsSessionCoordinator.submit(invocation)` and `cancelActive(reason)`.

- [ ] **Step 1: Write failing state-machine tests**

Use real in-memory fake sinks, not mocking frameworks. Test generation increments, stale chunk rejection, new request cancellation, one terminal callback, original fallback before first accepted PCM, no original replay after playback starts, strict-mode error, Breeno sink to AudioTrack sink downgrade, and cancellation during network read.

- [ ] **Step 2: Run tests and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.TtsSessionCoordinatorTest"`. Expected: missing coordinator.

- [ ] **Step 3: Implement minimal contracts and coordinator**

Serialize transitions with a `Mutex`, keep active job/generation in one state holder, and make every callback generation-aware. The coordinator owns the decoder for each request.

- [ ] **Step 4: Implement AudioTrack sink**

Map PCM format to Android channel/encoding constants, request transient speech audio focus, use streaming mode, write off the main thread, and always release focus and native resources in terminal paths.

- [ ] **Step 5: Run tests and verify GREEN**

Run targeted tests, all unit tests, and Robolectric construction tests for supported PCM formats.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/playback app/src/main/java/dev/breenottshook/session app/src/test
git commit -m "feat: coordinate cancellable TTS playback and fallback"
```

---

### Task 7: Module Settings App with Full Configuration

**Files:**
- Create: `app/src/test/java/dev/breenottshook/ui/SettingsOperationControllerBehaviorTest.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/MainActivity.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/SettingsOperationController.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/SettingsSchema.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/SettingsScreen.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/components/CharacterEmotionPicker.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/components/AdvancedSettings.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/components/DiagnosticsPanel.kt`

**Interfaces:**
- `SettingsOperationController.state: StateFlow<SettingsUiState>`
- intents `Edit`, `Save`, `RefreshCatalog`, `TestConnection`, `Preview`, `StopPreview`, `ResetDefaults`.
- `SettingsSchema` defines every field, validation metadata, section, and host-editor mapping.

- [ ] **Step 1: Write failing view-model tests**

Test unsaved draft isolation, validation blocking save, version-conflict reload, character refresh, emotion reset only when appropriate, manual values surviving refresh, connection result, preview using draft values, and save incrementing the shared version.

- [ ] **Step 2: Run tests and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.SettingsOperationControllerBehaviorTest"`.

- [ ] **Step 3: Implement the view model and shared schema**

The schema is UI-framework neutral so the injected host editor consumes the same keys, labels, defaults, ranges, and validation.

- [ ] **Step 4: Implement the Material 3 screen**

Render Basic, Voice, Advanced, and Debug sections; include dynamic dropdowns plus manual input, HTTP warning, connection test, preview/stop, save status, hook capability status, and diagnostics export with text redaction.

- [ ] **Step 5: Run tests, Compose preview compilation, and debug build**

Run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/ui app/src/test/java/dev/breenottshook/ui app/src/main/res
git commit -m "feat: add complete module configuration UI"
```

---

### Task 8: Yuki Hook Entry, Version Profiles, and Safe Transport Fallback

**Files:**
- Create: `app/src/test/java/dev/breenottshook/hook/ProfileSelectorTest.kt`
- Create: `app/src/test/java/dev/breenottshook/hook/TtsPayloadExtractorTest.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/HookEntry.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/VersionProfile.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/Breeno1183Profile.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/ProfileSelector.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/OriginalCall.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/TtsPayloadExtractor.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/OkHttpTtsFallback.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/HookDiagnostics.kt`

**Interfaces:**
- `VersionProfile.matches(packageVersion, ClassProbe): MatchResult`.
- `VersionProfile.install(HookRuntime): HookCapabilities`.
- `TtsPayloadExtractor.extract(String): ExtractedTtsRequest?` accepts only payloads containing a nonblank text field.
- Transport fallback activates only when the WebSocket request URL exactly matches the HeyTap TTS endpoint.

- [ ] **Step 1: Write failing selector and payload tests**

Test exact 11.8.3 preference, rejection of ambiguous candidates, no Hook on unsupported versions, extraction of documented/observed text keys, rejection of unrelated JSON, URL equality gating, and redacted diagnostics.

- [ ] **Step 2: Run tests and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.ProfileSelectorTest" --tests "*.TtsPayloadExtractorTest"`.

- [ ] **Step 3: Implement Yuki entry and profile selection**

Load only `com.heytap.speechassist`, initialize the hooked-process config cache, select the version profile, install stop/cancel interception, and publish capability status through `ConfigProvider`. Any missing or ambiguous member disables that capability without crashing the host.

- [ ] **Step 4: Implement the URL-gated OkHttp fallback**

Intercept WebSocket creation/send/close only for `wss://openapi-slp.heytapmobi.com/tts/ws`. Extract a TTS text payload, cancel the matching original socket, submit to the session coordinator, and use AudioTrack unless a verified Breeno adapter is available. Leave all unrelated sockets and HTTP calls untouched.

- [ ] **Step 5: Verify 11.8.3 exact profile against a real artifact**

Obtain the installed APK with:

```powershell
$apkPath = (& adb shell pm path com.heytap.speechassist | Select-Object -First 1) -replace '^package:',''
if (-not $apkPath) { throw 'com.heytap.speechassist is not installed on the connected device' }
& adb pull $apkPath work/breeno-11.8.3.apk
```

Confirm versionName/versionCode, the TTS request method, stop method, player entry, and callback members before placing their exact descriptors in `Breeno1183Profile`. If no device/APK is available, keep those capabilities disabled and report that only the URL-gated AudioTrack path is automatically verified; do not invent descriptors.

- [ ] **Step 6: Run unit tests and build**

Run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/hook app/src/test/java/dev/breenottshook/hook app/src/main/assets/xposed_init
git commit -m "feat: hook Breeno TTS with guarded compatibility profiles"
```

---

### Task 9: Inject the Complete Settings Editor into Breeno

**Files:**
- Create: `app/src/test/java/dev/breenottshook/hook/SettingsHostSelectorTest.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/SettingsHostSelector.kt`
- Create: `app/src/main/java/dev/breenottshook/hook/BreenoSettingsHook.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/host/HostSettingsContent.kt`
- Create: `app/src/main/java/dev/breenottshook/ui/host/HostFieldFactory.kt`

**Interfaces:**
- `SettingsHostSelector.select(version, availableClasses): HostDescriptor?`.
- `HostSettingsContent` renders every `SettingsSchema` field and delegates operations to `SettingsOperationController`.

- [ ] **Step 1: Write failing host-selector and schema parity tests**

Assert exact 11.8.3 host selection, ambiguous-host rejection, and that host editor keys exactly equal module-app schema keys.

- [ ] **Step 2: Run tests and verify RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests "*.SettingsHostSelectorTest"`.

- [ ] **Step 3: Implement settings entry injection**

For the verified 11.8.3 settings Fragment/Activity, append a “第三方音色” entry using the host context. On click, show the complete native dialog/editor built from `SettingsSchema`. If the exact host is unavailable, do not inject into arbitrary activities; retain the module app as the safe editor and publish the reason in diagnostics.

- [ ] **Step 4: Implement complete host-native editor**

Include enable, URL, connection test, character/emotion dropdown and manual values, language, speed, format, streaming, timeouts, fallback, all advanced generation fields, strict mode, force-module-player, log level, preview, stop, and save. Observe provider version changes while open.

- [ ] **Step 5: Run parity tests and build**

Run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/dev/breenottshook/hook app/src/main/java/dev/breenottshook/ui/host app/src/test
git commit -m "feat: inject complete shared settings into Breeno"
```

---

### Task 10: End-to-End Verification, Documentation, and APK Delivery

**Files:**
- Create: `README.md`
- Create: `docs/INSTALL.md`
- Create: `docs/COMPATIBILITY.md`
- Create: `docs/DIAGNOSTICS.md`
- Create: `scripts/verify.ps1`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- `scripts/verify.ps1` selects the local JDK 17 and runs unit tests, lint, and debug assembly in that order.
- Compatibility document distinguishes compiled, simulated, URL-fallback, exact-player, and on-device verification states.

- [ ] **Step 1: Write the verification script and user documentation**

Document LSPosed scope, Android 15 setup, HTTP privacy warning, both settings entries, character refresh, preview, fallback/strict behavior, log collection, and safe disable/recovery steps.

- [ ] **Step 2: Run complete automated verification**

Run:

```powershell
.\scripts\verify.ps1
```

Expected: unit tests pass, lint finishes without errors, and Debug APK is produced.

- [ ] **Step 3: Perform public API smoke test without user text**

Use only a fixed harmless phrase such as `你好，这是连接测试。`; verify `/character_list` and one non-streamed WAV response. Do not include real assistant history. Record latency, status, content type, WAV format, and whether the server supports the documented streamed form.

- [ ] **Step 4: Perform device verification when available**

Install the APK, enable only `com.heytap.speechassist` in LSPosed, restart the target, confirm Hook diagnostics, play a preview, trigger a Breeno response, interrupt playback, change the voice in both settings surfaces, and test fallback by using an unreachable endpoint.

- [ ] **Step 5: Copy the verified APK to the project output folder**

Create `outputs/` inside the project and copy the exact verified APK as `outputs/BreenoTTSHook-debug.apk`; record its SHA-256 in `docs/COMPATIBILITY.md`.

- [ ] **Step 6: Verify the working tree and commit**

```powershell
git status --short
git add README.md docs scripts app/src/main/res/values/strings.xml
git commit -m "docs: add installation and verification guide"
```

Expected: only the intentionally delivered APK under `outputs/` remains untracked if APK artifacts are excluded by `.gitignore`; all source and documentation are committed.
