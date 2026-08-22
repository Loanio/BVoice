# Task 3 Brief — align injected host settings page

## Objective

Bring the injected XiaoBu host settings page in line with the compact Compose flow: durable status, one stateful preview action, collapsed advanced settings, immediate core persistence, and accessible control labels.

## Scope

- `app/src/main/java/dev/breenottshook/ui/host/HostSettingsDialog.kt`
- `app/src/main/java/dev/breenottshook/ui/host/HostFieldFactory.kt`
- `app/src/test/java/dev/breenottshook/ui/host/HostSettingsDialogTest.kt` (new)
- `docs/DIAGNOSTICS.md`
- `docs/INSTALL.md`

Preserve unrelated dirty changes and existing TtsConfig/hook/transport/playback behavior.

## Required behavior

1. Confirm there is no production caller of `HostSettingsDialog.show()`; remove the obsolete dialog shell only if unused. Keep `createPageContent` as the host-page entry point.
2. Replace separate preview/stop actions with one stateful preview control. Expose a deterministic helper `HostSettingsDialog.previewActionLabel(isPreviewing: Boolean): String` and test both states.
3. Keep refresh/test/preview operations mutually exclusive and show result/status text in the page, not Toast-only feedback. Reuse the existing `SettingsViewModel` operation/service status where applicable; do not duplicate persistence semantics.
4. Make core toggles and character/emotion selection persist immediately; advanced fields remain behind a disclosure and explicit save.
5. Add accessible switch content descriptions that include label and current state through `HostFieldFactory.switchContentDescription(label, checked)`, with unit tests.
6. Update diagnostics/install docs to describe automatic initial catalog loading, immediate core saves, advanced explicit save, and durable connection status.

## Verification

- Add focused host unit tests first (RED), then GREEN.
- Run `scripts/verify.ps1` with JDK 17 after implementation.
- Commit only scoped Task 3 files (plus required review/ledger files if needed); report commit hash and test evidence.
