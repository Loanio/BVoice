# Task 4 report: immediate playback start

## Changes

- `AudioTrackSink.write` now ignores empty PCM segments, creates/plays the `AudioTrack` on the first non-empty PCM write, and writes that segment immediately. It no longer retains all PCM until `complete()`.
- Added `AudioTrackStartPolicy` and a focused test documenting the non-empty-first-write contract.

This makes the coordinator's `onUtteranceStarted` callback occur after the first PCM write has reached a started `AudioTrack`, rather than after a complete-response buffer is flushed.

## Verification

`AudioTrackStartPolicyTest`, `PcmWriteLoopTest`, and `StartupBufferGateTest` all passed via Gradle (`BUILD SUCCESSFUL`).

## Concern

The Android `AudioTrack` itself is platform-backed, so the focused unit test covers the start gate contract; the sink integration path was verified by compilation and code review of the first-write ordering.
