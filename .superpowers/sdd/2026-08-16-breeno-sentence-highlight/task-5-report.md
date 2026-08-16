# Task 5 report: engine-first profile routing

## Changes

- `BreenoHooker.installForContext` now probes the target engine class through `appClassLoader` and validates its `EngineTtsInstaller` descriptor before profile selection.
- When the 12.9.9 engine descriptor resolves, the hook forcibly selects `Breeno1299Profile` even if `PackageManager` reports an older installed/version-mismatched package value.
- When the engine is unavailable, existing version/class profile selection remains unchanged, including the 11.8.3 WebSocket fallback and ambiguity rejection.
- Added an engine probe diagnostic containing class name, resolution result, and package version without logging sensitive speech text.
- Added profile selector tests for dual-version preference and fallback behavior.

## Verification

`gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.hook.ProfileSelectorTest --no-daemon`

Result: `BUILD SUCCESSFUL`.

This also compiled the modified Hooker and profile classes through the debug Kotlin/unit-test build.

## Concerns

The production probe validates the exact engine descriptor before forcing 12.9.9; if an APK has the class but changed method signatures, it correctly falls back to normal profile selection rather than installing a broken engine hook.

## Review follow-up

Updated `TransportDescriptorTest` to assert the current engine methods `D0`, `G`, `O0`, and `J0`. The successful probe now retains the exact `Class<*>` loaded through `appClassLoader` and passes it to `installEngine`, avoiding a second potentially different-loader lookup; fallback routes retain the existing `toClassOrNull` path.
