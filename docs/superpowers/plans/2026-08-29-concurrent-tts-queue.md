# Concurrent TTS Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Concurrently prepare later GPT-SoVITS sentences, play prepared PCM strictly in source order, insert a configurable audible sentence interval, and expose validated concurrency/interval settings in both settings surfaces.

**Architecture:** Multi-utterance sessions use a sliding window of `Deferred<Result<PreparedUtterance>>` values. The window size is the positive user-supplied `maxConcurrentSynthesis`; consuming position `i` starts position `i + windowSize` before position `i` is written, allowing synthesis to overlap playback while bounding in-flight and prepared results. A dedicated PCM silence helper inserts frame-aligned zero samples between ordered utterances.

**Tech Stack:** Kotlin 2.1, kotlinx-coroutines, kotlinx-serialization, Android AudioTrack contracts, Jetpack Compose, Android native views, JUnit 4, kotlinx-coroutines-test, Robolectric.

**Spec:** `docs/superpowers/specs/2026-08-29-concurrent-tts-queue-design.md`

## Global Constraints

- `maxConcurrentSynthesis` defaults to `3`, accepts every positive `Int`, and has no software maximum or silent clamping.
- `playbackIntervalMs` defaults to `0` and accepts `0..5000` milliseconds.
- Playback and `onUtteranceStarted` callbacks remain strictly ordered even when synthesis completes out of order.
- Original-TTS fallback remains controlled by `fallbackToOriginal` and `strictMode`, and is never used after third-party PCM starts.
- Invalid routine input must not produce Toast messages or overwrite the last valid configuration.
- Preserve unrelated existing modifications in `HostSettingsContent.kt` and all other dirty worktree files.

---

### Task 1: Persist and Validate Queue Settings

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/config/TtsConfig.kt`
- Modify: `app/src/main/java/dev/breenottshook/config/ConfigValidator.kt`
- Modify: `app/src/main/java/dev/breenottshook/ui/SettingsSchema.kt`
- Test: `app/src/test/java/dev/breenottshook/config/ConfigValidatorTest.kt`
- Test: `app/src/test/java/dev/breenottshook/ui/SettingsSchemaTest.kt`

**Interfaces:**
- Produces: `TtsConfig.maxConcurrentSynthesis: Int`
- Produces: `TtsConfig.playbackIntervalMs: Long`
- Produces: schema keys `maxConcurrentSynthesis` and `playbackIntervalMs` in `SettingsSection.ADVANCED`

- [ ] **Step 1: Add failing configuration tests**

Extend `ConfigValidatorTest` with defaults, codec round-trip, positive unbounded concurrency, and interval range tests:

```kotlin
@Test
fun `queue settings use safe defaults`() {
    assertEquals(3, TtsConfig().maxConcurrentSynthesis)
    assertEquals(0L, TtsConfig().playbackIntervalMs)
}

@Test
fun `accepts positive concurrency without an upper bound`() {
    assertTrue(ConfigValidator.validate(TtsConfig(maxConcurrentSynthesis = Int.MAX_VALUE)) is ValidationResult.Valid)
    assertInvalid("maxConcurrentSynthesis", TtsConfig(maxConcurrentSynthesis = 0))
}

@Test
fun `validates playback interval`() {
    assertTrue(ConfigValidator.validate(TtsConfig(playbackIntervalMs = 5_000)) is ValidationResult.Valid)
    assertInvalid("playbackIntervalMs", TtsConfig(playbackIntervalMs = -1))
    assertInvalid("playbackIntervalMs", TtsConfig(playbackIntervalMs = 5_001))
}
```

Add both non-default fields to the existing codec round-trip fixture.

- [ ] **Step 2: Add failing schema tests**

Update the expected key set and assert the title and typed edits:

```kotlin
assertEquals("高级设置", SettingsSection.ADVANCED.title)
assertEquals(12, success(SettingsSchema.edit(TtsConfig(), "maxConcurrentSynthesis", "12")).maxConcurrentSynthesis)
assertEquals(450L, success(SettingsSchema.edit(TtsConfig(), "playbackIntervalMs", "450")).playbackIntervalMs)
assertEquals(
    SchemaEditResult.Invalid("maxConcurrentSynthesis", "请输入整数"),
    SettingsSchema.edit(TtsConfig(), "maxConcurrentSynthesis", "")
)
```

- [ ] **Step 3: Run targeted tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.ConfigValidatorTest" --tests "*.SettingsSchemaTest"
```

Expected: compilation failures because the two `TtsConfig` fields do not exist, followed by title/key expectation failures after the model compiles.

- [ ] **Step 4: Implement the minimal configuration and schema changes**

Add to `TtsConfig`:

```kotlin
val maxConcurrentSynthesis: Int = 3,
val playbackIntervalMs: Long = 0,
```

Add semantic validation:

```kotlin
if (config.maxConcurrentSynthesis < 1) {
    add(ConfigIssue("maxConcurrentSynthesis", "并发请求数量必须大于 0"))
}
if (config.playbackIntervalMs !in 0L..5_000L) {
    add(ConfigIssue("playbackIntervalMs", "播放间隔必须位于 0 到 5000 毫秒"))
}
```

Rename `SettingsSection.ADVANCED("高级生成")` to `SettingsSection.ADVANCED("高级设置")`. Add two integer schema fields and edit branches:

```kotlin
integer("maxConcurrentSynthesis", "并发请求数量", "必须大于 0；数值过大可能增加服务和内存压力", SettingsSection.ADVANCED, minimum = 1.0)
integer("playbackIntervalMs", "播放间隔（毫秒）", "相邻句子之间的静音时长，范围 0–5000", SettingsSection.ADVANCED, minimum = 0.0, maximum = 5_000.0)
```

Parse concurrency with `toIntOrNull()` and interval with `toLongOrNull()`; do not clamp either value.

- [ ] **Step 5: Run targeted tests and verify GREEN**

Run the command from Step 3. Expected: all targeted tests pass.

- [ ] **Step 6: Commit the configuration slice**

```powershell
git add app/src/main/java/dev/breenottshook/config/TtsConfig.kt app/src/main/java/dev/breenottshook/config/ConfigValidator.kt app/src/main/java/dev/breenottshook/ui/SettingsSchema.kt app/src/test/java/dev/breenottshook/config/ConfigValidatorTest.kt app/src/test/java/dev/breenottshook/ui/SettingsSchemaTest.kt
git commit -m "feat: configure concurrent TTS preparation"
```

---

### Task 2: Prepare Complete Utterances and Generate PCM Silence

**Files:**
- Create: `app/src/main/java/dev/breenottshook/session/PreparedUtterance.kt`
- Create: `app/src/main/java/dev/breenottshook/audio/PcmSilence.kt`
- Create: `app/src/test/java/dev/breenottshook/audio/PcmSilenceTest.kt`
- Modify: `app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt`

**Interfaces:**
- Produces: `PreparedUtterance(val utterance: TtsUtterance, val segments: List<PcmSegment>)`
- Produces: `PcmSilence.create(format: PcmFormat, durationMs: Long): PcmSegment?`

- [ ] **Step 1: Write failing silence tests**

Create `PcmSilenceTest`:

```kotlin
@Test
fun `creates frame aligned zero PCM for requested duration`() {
    val format = PcmFormat(sampleRate = 24_000, channels = 1, bitsPerSample = 16)
    val segment = requireNotNull(PcmSilence.create(format, 250))
    assertEquals(12_000, segment.bytes.size)
    assertTrue(segment.bytes.all { it == 0.toByte() })
}

@Test
fun `zero interval produces no segment`() {
    assertNull(PcmSilence.create(PcmFormat(24_000, 1, 16), 0))
}
```

Also test a stereo 24-bit format to prove frame alignment.

- [ ] **Step 2: Run silence tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.PcmSilenceTest"
```

Expected: compilation failure because `PcmSilence` does not exist.

- [ ] **Step 3: Implement minimal silence and prepared-result models**

Implement `PcmSilence.create` with integer frame math:

```kotlin
val frameSize = format.channels * format.bitsPerSample / 8
val frames = format.sampleRate.toLong() * durationMs / 1_000L
val byteCount = Math.multiplyExact(frames, frameSize.toLong())
require(byteCount <= Int.MAX_VALUE)
return PcmSegment(format, ByteArray(byteCount.toInt()))
```

Return `null` for zero duration and require non-negative input. Add the immutable `PreparedUtterance` data class.

- [ ] **Step 4: Run silence tests and verify GREEN**

Run the command from Step 2. Expected: all silence tests pass.

- [ ] **Step 5: Commit the PCM preparation slice**

```powershell
git add app/src/main/java/dev/breenottshook/audio/PcmSilence.kt app/src/main/java/dev/breenottshook/session/PreparedUtterance.kt app/src/test/java/dev/breenottshook/audio/PcmSilenceTest.kt
git commit -m "feat: prepare utterance PCM and sentence silence"
```

---

### Task 3: Add Sliding-Window Concurrent Synthesis

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/session/TtsSessionCoordinator.kt`
- Modify: `app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `TtsConfig.maxConcurrentSynthesis`
- Consumes: `TtsConfig.playbackIntervalMs`
- Consumes: `PreparedUtterance`
- Consumes: `PcmSilence.create`
- Preserves: `submit`, `submitStream`, `cancelActive`, and `state` public signatures

- [ ] **Step 1: Write a failing overlap test**

Add a blocking sink and synthesis probes that demonstrate sentence three begins synthesis while sentence one is blocked in playback, with concurrency two:

```kotlin
@Test
fun `stream prepares later utterances while current utterance is playing`() = runTest {
    val firstWriteEntered = CompletableDeferred<Unit>()
    val releaseFirstWrite = CompletableDeferred<Unit>()
    val synthesized = mutableListOf<String>()
    val coordinator = coordinator(
        config = TtsConfig(maxConcurrentSynthesis = 2),
        sink = BlockingRecordingSink(firstWriteEntered, releaseFirstWrite),
        engine = SynthesisEngine { text, _, onBytes ->
            synthesized += text
            onBytes(WavFixtures.pcmWav(byteArrayOf(text.first().code.toByte(), 0)))
        }
    )

    coordinator.submitStream(utterances("a", "b", "c"), RecordingCallbacks(), OriginalCall {})
    firstWriteEntered.await()
    advanceUntilIdle()

    assertTrue("c" in synthesized)
    releaseFirstWrite.complete(Unit)
    advanceUntilIdle()
}
```

Structure the test sink so virtual-time advancement does not deadlock; only the first real utterance write blocks, not silence writes.

- [ ] **Step 2: Write failing concurrency and ordering tests**

Add tests that track `activeCalls`/`maxActiveCalls`, release synthesis gates out of order, and assert:

```kotlin
assertEquals(configuredConcurrency, maxActiveCalls)
assertArrayEquals(byteArrayOf(1, 0, 2, 0, 3, 0), sink.utteranceBytes())
assertEquals(listOf(0, 1, 2), callbacks.utteranceStarts)
```

Use more utterances than the configured window. Add an `Int.MAX_VALUE` configuration test with only four utterances and assert only four jobs start.

- [ ] **Step 3: Write failing cancellation and fallback matrix tests**

Cover cancellation with multiple blocked synthesis jobs, first-position failure with all four combinations of `fallbackToOriginal`/`strictMode`, and a later failure after PCM starts. Assert every in-flight job receives cancellation, the sink cancels once, original fallback runs at most once, and no stale bytes are written.

- [ ] **Step 4: Write failing sentence-interval integration tests**

For 24 kHz mono 16-bit PCM and `playbackIntervalMs = 1`, assert the sink receives 48 zero bytes between two utterance payloads. Assert no silence segment is written after the final utterance or when the interval is zero.

- [ ] **Step 5: Run coordinator tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.TtsSessionCoordinatorTest"
```

Expected: overlap, concurrency, ordering, cancellation, or silence assertions fail because synthesis is currently serial and no interval is inserted.

- [ ] **Step 6: Implement per-utterance preparation**

Extract a private suspend function in `TtsSessionCoordinator`:

```kotlin
private suspend fun prepareUtterance(
    session: ActiveSession,
    utterance: TtsUtterance,
    config: TtsConfig
): PreparedUtterance {
    val decoder = StreamingWavDecoder()
    val segments = mutableListOf<PcmSegment>()
    synthesisEngine.synthesize(utterance.text, config) { bytes ->
        ensureCurrent(session.generation)
        segments += decoder.feed(bytes)
    }
    ensureCurrent(session.generation)
    check(decoder.finish() == DecodeFinish.Complete) { "Truncated WAV response" }
    check(segments.any { it.bytes.isNotEmpty() }) { "Synthesis produced no playable PCM" }
    return PreparedUtterance(utterance, segments)
}
```

Do not let producer jobs call `AudioSink` or host callbacks.

- [ ] **Step 7: Implement the ordered sliding window**

Inside `runSession`, keep the single-utterance path unchanged and use `supervisorScope` for streams. Start `minOf(config.maxConcurrentSynthesis, utteranceCount)` async jobs that return `Result<PreparedUtterance>` while rethrowing `CancellationException`. For each ordered position:

```kotlin
val prepared = jobs[position]!!.await().getOrThrow()
val nextPosition = position + windowSize
if (nextPosition < utterances.size) jobs[nextPosition] = launchPreparation(nextPosition)
playPrepared(session, prepared, config, hasNext = position < utterances.lastIndex)
```

Starting `nextPosition` before `playPrepared` overlaps the new request with current AudioTrack writes. Because only `windowSize` deferred slots are active or prepared, queue memory follows the user-selected concurrency. Use list position for ordering; do not assume `TtsUtterance.index` is contiguous.

- [ ] **Step 8: Implement ordered playback and fallback preservation**

Move sink opening/writing and callback transitions into `playPrepared`. After an utterance’s real segments, write `PcmSilence.create(lastFormat, config.playbackIntervalMs)` only when `hasNext` is true. Preserve generation checks immediately before and after every sink write.

Keep the existing terminal catch logic unchanged in meaning:

```kotlin
if (!played && config.fallbackToOriginal && !config.strictMode) {
    session.originalCall.resume()
} else {
    session.callbacks.onError(error)
}
```

Ensure the `supervisorScope` exits by cancelling unfinished producer children when ordered playback encounters a failure.

- [ ] **Step 9: Run coordinator tests and verify GREEN**

Run the command from Step 5. Expected: all session tests pass with no leaked test coroutines.

- [ ] **Step 10: Run all audio and session tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.breenottshook.audio.*" --tests "dev.breenottshook.session.*"
```

Expected: all tests pass.

- [ ] **Step 11: Commit the concurrent queue slice**

```powershell
git add app/src/main/java/dev/breenottshook/session/TtsSessionCoordinator.kt app/src/main/java/dev/breenottshook/session/PreparedUtterance.kt app/src/main/java/dev/breenottshook/audio/PcmSilence.kt app/src/test/java/dev/breenottshook/session/TtsSessionCoordinatorTest.kt app/src/test/java/dev/breenottshook/audio/PcmSilenceTest.kt
git commit -m "feat: synthesize queued utterances concurrently"
```

---

### Task 4: Expose Validated Fields in Both Settings Surfaces

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/dev/breenottshook/ui/host/HostFieldFactory.kt`
- Modify: `app/src/main/java/dev/breenottshook/ui/host/HostStrings.kt`
- Carefully modify existing dirty file: `app/src/main/java/dev/breenottshook/ui/host/HostSettingsContent.kt`
- Modify: `app/src/test/java/dev/breenottshook/ui/SettingsScreenTest.kt`
- Modify: `app/src/test/java/dev/breenottshook/ui/host/HostFieldFactoryTest.kt`
- Create: `app/src/test/java/dev/breenottshook/ui/host/HostInputValidationTest.kt`

**Interfaces:**
- Consumes: the two schema fields from Task 1
- Produces: field-level host validation without routine Toasts
- Preserves: existing automatic-save and preview behavior

- [ ] **Step 1: Write failing module UI tests**

Expand the advanced section, assert “高级设置”, “并发请求数量”, and “播放间隔（毫秒）” are present, and verify both fields use numeric editors. Add a test that invalid semantic config produces supporting error text without a Toast dependency.

- [ ] **Step 2: Write failing host parity and localization tests**

Update `HostFieldFactoryTest` to assert both new bindings are `EditText`, read the configured values, and use numeric input types. Add assertions for Chinese and English labels/descriptions and confirm `HostFieldFactory.supportedKeys == SettingsSchema.fields.map { it.key }.toSet()`.

- [ ] **Step 3: Write failing host validation tests**

Introduce a small pure helper, wished-for as:

```kotlin
HostInputValidation.validate(config, bindings): HostValidationResult
```

Test that it returns the exact invalid field/message for malformed concurrency and interval values and returns a valid `TtsConfig` for `Int.MAX_VALUE` concurrency. This helper must not depend on Toast or Android lifecycle state.

- [ ] **Step 4: Run UI tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.SettingsScreenTest" --tests "*.HostFieldFactoryTest" --tests "*.HostInputValidationTest"
```

Expected: missing keys/helper and label assertions fail.

- [ ] **Step 5: Wire shared fields into module and host editors**

Add both keys to `SettingsScreen.fieldValue` and `HostFieldFactory.read`. Add localized labels/descriptions in `HostStrings`. Keep `SettingsSchema` as the source of section placement so both fields appear under “高级设置”.

- [ ] **Step 6: Implement quiet host validation**

Create `HostInputValidation` to fold raw bindings through `SettingsSchema.edit`, then call `ConfigValidator.validate`. Return either the validated config or a field/message pair. In `HostSettingsContent`, replace routine `toast("请先修正输入格式")` branches with field presentation:

```kotlin
editor.error = message
editor.requestFocus()
```

Clear the previous error when that field becomes valid. Do not add Toasts on text changes, automatic saves, successful saves, range normalization, or focus changes. Preserve Toasts for explicit clipboard/provider operation failures. Remove the existing restore-default success Toast because the updated controls already communicate the change.

- [ ] **Step 7: Run UI tests and verify GREEN**

Run the command from Step 4. Expected: all targeted UI tests pass.

- [ ] **Step 8: Run the complete unit suite and debug build**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, all unit tests pass, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 9: Review the dirty-file overlap before committing**

Run:

```powershell
git diff -- app/src/main/java/dev/breenottshook/ui/host/HostSettingsContent.kt
git status --short
```

Confirm only queue-setting validation changes were added on top of the user’s pre-existing Host settings work. Do not stage unrelated dirty files.

- [ ] **Step 10: Commit only this task’s files**

```powershell
git add app/src/main/java/dev/breenottshook/ui/SettingsScreen.kt app/src/main/java/dev/breenottshook/ui/host/HostFieldFactory.kt app/src/main/java/dev/breenottshook/ui/host/HostStrings.kt app/src/main/java/dev/breenottshook/ui/host/HostSettingsContent.kt app/src/main/java/dev/breenottshook/ui/host/HostInputValidation.kt app/src/test/java/dev/breenottshook/ui/SettingsScreenTest.kt app/src/test/java/dev/breenottshook/ui/host/HostFieldFactoryTest.kt app/src/test/java/dev/breenottshook/ui/host/HostInputValidationTest.kt
git commit -m "feat: expose concurrent playback settings"
```

---

### Task 5: Final Verification and Diff Audit

**Files:**
- Verify all files modified by Tasks 1–4
- Do not modify unrelated dirty worktree files

**Interfaces:**
- Produces: verified debug APK and evidence-backed completion report

- [ ] **Step 1: Run focused regression tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.ConfigValidatorTest" --tests "*.SettingsSchemaTest" --tests "*.PcmSilenceTest" --tests "*.TtsSessionCoordinatorTest" --tests "*.SettingsScreenTest" --tests "*.HostFieldFactoryTest" --tests "*.HostInputValidationTest"
```

Expected: all focused tests pass.

- [ ] **Step 2: Run full verification**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no lint errors.

- [ ] **Step 3: Audit the final diff and worktree ownership**

```powershell
git diff --check
git status --short
git diff --stat HEAD~4..HEAD
```

Confirm no credential, utterance text, generated binary, screenshot, or unrelated pre-existing UI change was accidentally staged. Report separately whether automated tests, build, and on-device behavior were verified.
