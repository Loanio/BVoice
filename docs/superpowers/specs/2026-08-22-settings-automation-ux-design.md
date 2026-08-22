# Settings Automation UX Design

## Goal

Make third-party TTS setup feel like a native Breeno preference page: users should be able to enable the feature, obtain a usable voice, select it, and preview it with minimal manual work and unambiguous feedback.

## Scope

This change applies to both settings surfaces that share `TtsConfig`:

- the module app's Compose settings screen;
- the injected Breeno host settings page.

It does not change the TTS transport, playback, configuration schema, or hook behavior.

## Interaction Model

### Core settings

The initial view contains only:

1. A persistent service status row: unchecked, checking, available, or unavailable, including the most recent result text.
2. The enable switch.
3. Character and emotion selectors.
4. One preview control. It reads `试听` while idle and `停止试听` while audio is playing.

Character data is refreshed automatically when the page opens if no catalog is in memory. Users may explicitly refresh from the voice section. During refresh, voice selectors remain visible but unavailable until a cached or fresh catalog is available.

### Saving

Low-risk choices (`enabled`, `character`, `emotion`, `useManualVoice`, and fallback mode) are persisted as soon as the user changes them. The module app exposes an error state if that write fails. The host page follows the same behavior through the shared operation controller.

Text and numeric transport parameters remain draft values. They live in the advanced section and use an explicit save action. The UI shows whether advanced changes remain unsaved.

### Advanced settings

All service URL, manual voice identifiers, generation parameters, streaming, timeouts, player controls, test text, and diagnostics move below a collapsed `高级设置` disclosure. The HTTP warning lives beside the service URL and includes its practical consequence rather than occupying the top of the screen.

## Shared State And Operations

A surface-neutral settings operation controller owns catalog refresh, connection testing, preview lifecycle, persistence outcomes, and busy-state exclusion. Compose and the host page observe the same derived state, so their labels, disabled controls, success copy, and failure copy cannot drift.

At most one network/media operation runs at a time. While an operation is in progress, its initiating control becomes visibly busy and incompatible controls are disabled. Connection results remain in the service status row; Toast may supplement this but is not the only record of the result.

## Accessibility

Each setting row presents its label, description, value/state, and action as one accessible target. Switches announce their own label and checked state. Busy and completion changes are announced. Buttons keep a minimum 48dp height.

## Cleanup

The obsolete `HostSettingsDialog.show()` AlertDialog path is removed after confirming the injected page is its only production caller. Host and Compose screens reuse the shared operations instead of retaining independent refresh/test/preview/save implementations.

## Error Handling

- Catalog refresh failure keeps the last cached choices and presents the failure in the service status row.
- Connection failure preserves the selected configuration and offers retry.
- Preview errors return the single preview control to idle and retain a readable error message.
- Immediate persistence failure restores the preceding visible value and reports the failure.
- Draft validation errors remain next to the invalid advanced field.

## Tests

Unit tests cover single preview-action state, mutually exclusive busy operations, automatic catalog refresh decision, immediate-persist success/failure, advanced disclosure state, and durable connection feedback. Existing configuration validation and streaming tests remain unchanged.
