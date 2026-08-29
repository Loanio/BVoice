# Concurrent TTS Queue Design

## Goal

Reduce the silence between streamed utterances by synthesizing later sentences concurrently while the current sentence is still playing, without changing sentence order, cancellation semantics, host callbacks, or user-controlled fallback behavior.

The embedded settings section currently named “高级生成” will be renamed to “高级设置”. Two integer settings will be added there: the synthesis concurrency count and the audible interval between sentences.

## Configuration

`TtsConfig` gains two serialized fields:

- `maxConcurrentSynthesis: Int = 3`
- `playbackIntervalMs: Long = 0`

`maxConcurrentSynthesis` must be a positive integer. It intentionally has no software-defined maximum. The exact configured value is used as the concurrency permit count. The UI should explain that excessive values can overload the synthesis server, create many simultaneous connections, and increase process memory use, but it must not silently clamp a valid positive value.

`playbackIntervalMs` must be in `0..5000`. It represents an audible silent interval inserted between consecutive utterances. Zero disables the interval.

Both values are part of the versioned configuration document, pass through `ConfigCodec`, and are available to the hooked process through the existing configuration provider and `HookConfigCache`. Older stored JSON remains compatible through the fields’ defaults.

## UI and Validation

`SettingsSection.ADVANCED` is renamed from “高级生成” to “高级设置”. Because the module UI and embedded UI derive fields from `SettingsSchema`, both surfaces receive the same fields and labels:

- “并发请求数量” — positive integer, default 3, no maximum.
- “播放间隔（毫秒）” — integer from 0 through 5000, default 0.

Validation is owned by `SettingsSchema.edit` for parse errors and `ConfigValidator` for semantic ranges. Invalid text, empty values, non-integers, zero or negative concurrency, and out-of-range intervals do not replace the last valid configuration.

Routine editing must not produce Toast messages. The module UI presents field-level validation text. The embedded UI keeps the editor open and identifies the invalid field using its existing validation presentation. Toasts are reserved for an explicit operation that cannot complete, such as persistence or provider failure; successful edits, focus changes, normalization, and ordinary validation do not show Toasts.

## Concurrent Synthesis Architecture

Only multi-utterance `submitStream` sessions use the concurrent queue. Ordinary single-utterance `submit` behavior remains unchanged.

At session start, every ordered `TtsUtterance` becomes a synthesis job. A session-local concurrency gate allows at most `maxConcurrentSynthesis` synthesis jobs to execute at once. Each job:

1. Calls the existing `SynthesisEngine.synthesize` with the session configuration.
2. Decodes response chunks with its own `StreamingWavDecoder`.
3. Accumulates validated `PcmSegment` values into a `PreparedUtterance` result.
4. Publishes success or failure under the utterance’s original list position.

Synthesis completion order does not affect playback order. The playback consumer waits for result position zero, then one, and so on. A completed later result remains prepared until all earlier positions have been consumed.

The session keeps no more prepared, unconsumed utterances than the configured concurrency count. A playback-consumed result releases queue capacity so another pending synthesis job can publish and the scheduler can continue. This bounds prepared PCM retained by the queue relative to the user’s chosen concurrency, while still placing all later sentences into the work queue.

Every prepared result remains subject to the existing per-response and WAV limits. No temporary files are introduced.

## Playback and Sentence Interval

The playback consumer uses the session’s existing `AudioSink`, preserving one continuous `AudioTrack` where the PCM format does not change. For each prepared utterance it:

1. Opens or reopens the sink when the PCM format changes.
2. Writes that utterance’s PCM segments in original order.
3. Emits `onUtteranceStarted` only after the first non-empty segment has been successfully written.
4. Emits the session-level `onStarted` only after the first non-empty segment in the session has been successfully written.
5. Inserts the configured silent PCM interval before the next utterance when another utterance remains.

The interval is represented as zero-valued PCM frames in the active format. This guarantees an audible interval inside the AudioTrack stream; a wall-clock delay alone could overlap audio already buffered by AudioTrack and would not reliably produce the requested gap. Frame calculation uses the sample rate, channel count, and bits per sample and emits frame-aligned bytes.

## Cancellation and Generation Safety

The concurrent synthesis jobs and ordered playback consumer are children of the active session job. A new request, host stop, mute action, or explicit cancellation cancels the entire session tree.

Cancellation must:

- cancel every in-flight OkHttp call through the existing coroutine cancellation bridge;
- prevent prepared results from an old generation from reaching the sink;
- clear queued prepared results;
- cancel and release the sink exactly once;
- emit at most one terminal callback.

Generation checks remain immediately before and after sink operations. A synthesis job that ignores or delays cancellation cannot write directly to the sink; only the generation-checked ordered consumer can perform playback.

## Failure and Original-TTS Fallback

Fallback behavior is determined from the session’s captured `TtsConfig`:

- If no third-party PCM has started playback, `fallbackToOriginal` is true, and `strictMode` is false, cancel all concurrent synthesis jobs and resume the preserved original TTS call exactly once.
- If `fallbackToOriginal` is false or `strictMode` is true, report the error and do not call the original TTS.
- If any third-party PCM has started playback, never replay the original utterance, regardless of the fallback switch, because doing so would duplicate already spoken content.

A failure from a later position is consumed according to sentence order. Earlier successfully prepared sentences remain eligible for ordered playback. When the failed position becomes current, the session terminates under the rules above and cancels remaining work.

## Tests

Unit tests will prove:

- a multi-utterance session starts later synthesis while an earlier utterance is still being played;
- active synthesis calls never exceed the configured positive concurrency value;
- concurrency values greater than conventional limits are accepted without clamping;
- out-of-order synthesis completion still produces ordered PCM writes and callbacks;
- prepared-result backpressure follows the configured concurrency;
- cancellation terminates all in-flight work and prevents stale writes;
- first-position failure follows `fallbackToOriginal` and `strictMode` combinations;
- failure after playback starts never resumes original TTS;
- inserted silence contains the correct frame-aligned number of zero bytes;
- zero interval inserts no silence;
- schema editing and configuration validation reject malformed values without changing persisted configuration;
- module and embedded UI schemas contain the same two new settings;
- the advanced section title is “高级设置”.

The targeted session, configuration, schema, and host-field tests run first, followed by the complete unit-test suite and a debug APK build.

## Scope

This change does not add a new network protocol, change the GPT-SoVITS request payload, persist synthesized audio, change Hook descriptors, or adopt the host’s native player. It keeps the existing AudioTrack sink and existing LSPosed scope.
