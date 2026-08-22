# Settings Automation UX Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Automate routine TTS setup and turn both settings surfaces into a compact, native-feeling flow.

**Architecture:** SettingsViewModel becomes the shared source for operation state and durable service feedback. Compose and the injected host page render that state with their own widget systems; they retain one configuration schema and one persistence path.

**Tech Stack:** Kotlin, Coroutines StateFlow, Compose Material 3, Android Views, Robolectric, JUnit 4.

**Spec:** docs/superpowers/specs/2026-08-22-settings-automation-ux-design.md

## Global Constraints

- Do not alter TtsConfig, Hook, transport, playback, or versioned configuration semantics.
- Preserve all existing unstaged worktree changes.
- Show one preview action and never use Toast as the only record of service outcome.
- Run tests with JDK 17 as configured in scripts/verify.ps1.

---

### Task 1: Shared settings operation state

**Files:**
- Modify: app/src/main/java/dev/breenottshook/ui/SettingsViewModel.kt
- Modify: app/src/test/java/dev/breenottshook/ui/SettingsViewModelTest.kt

**Interfaces:**
- Produces SettingsOperation with IDLE, REFRESHING_CATALOG, TESTING_CONNECTION, and PREVIEWING.
- Produces ServiceStatus with UNCHECKED, CHECKING, AVAILABLE, and UNAVAILABLE.
- Adds operation, serviceStatus, and serviceStatusMessage to SettingsUiState.

- [ ] Step 1: Write failing tests

    @Test
    fun connectionResultRemainsInServiceStatusAfterTestCompletes() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSettingsRepository(ConfigSnapshot(0, TtsConfig())))
        viewModel.testConnection()
        advanceUntilIdle()
        assertEquals(ServiceStatus.AVAILABLE, viewModel.state.value.serviceStatus)
        assertEquals("连接成功", viewModel.state.value.serviceStatusMessage)
        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)
    }

- [ ] Step 2: Verify RED

Run: ./gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.ui.SettingsViewModelTest

Expected: compilation failure because the new state types and fields do not exist.

- [ ] Step 3: Implement minimum state machine

    private fun begin(operation: SettingsOperation): Boolean {
        if (mutableState.value.operation != SettingsOperation.IDLE) return false
        mutableState.value = mutableState.value.copy(operation = operation)
        return true
    }

Guard refresh, connection test, and preview with begin; publish checking, available, or unavailable status and always return to idle.

- [ ] Step 4: Verify GREEN

Run: ./gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.ui.SettingsViewModelTest

Expected: PASS.

- [ ] Step 5: Commit only Task 1 files

Run: git add -- app/src/main/java/dev/breenottshook/ui/SettingsViewModel.kt app/src/test/java/dev/breenottshook/ui/SettingsViewModelTest.kt; git commit -m "feat: expose settings operation status"

### Task 2: Automate core setup and simplify Compose

**Files:**
- Modify: app/src/main/java/dev/breenottshook/ui/MainActivity.kt
- Modify: app/src/main/java/dev/breenottshook/ui/SettingsViewModel.kt
- Modify: app/src/main/java/dev/breenottshook/ui/SettingsScreen.kt
- Modify: app/src/test/java/dev/breenottshook/ui/SettingsViewModelTest.kt
- Modify: app/src/test/java/dev/breenottshook/ui/SettingsScreenTest.kt

**Interfaces:**
- Produces loadInitialCatalog() and updateCoreSetting(transform: (TtsConfig) -> TtsConfig).
- The Compose screen consumes state and exposes a collapsed 高级设置 disclosure.

- [ ] Step 1: Write failing tests

    @Test
    fun coreSettingPersistsImmediatelyAndClearsUnsavedState() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(3, TtsConfig(enabled = false)))
        val viewModel = viewModel(repository)
        viewModel.updateCoreSetting { it.copy(enabled = true) }
        advanceUntilIdle()
        assertTrue(repository.snapshot.value.enabled)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun initialScreenHidesAdvancedFieldsAndShowsOnePreviewAction() {
        composeRule.setContent { screenFor(SettingsUiState(0, TtsConfig(), TtsConfig())) }
        composeRule.onNodeWithText("试听").assertIsDisplayed()
        composeRule.onNodeWithText("停止试听").assertDoesNotExist()
        composeRule.onNodeWithText("API 地址").assertDoesNotExist()
        composeRule.onNodeWithText("高级设置").assertIsDisplayed()
    }

- [ ] Step 2: Verify RED

Run: ./gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.ui.SettingsViewModelTest --tests dev.breenottshook.ui.SettingsScreenTest

Expected: failure because immediate persistence and the collapsed hierarchy do not exist.

- [ ] Step 3: Implement minimum automatic flow

Call loadInitialCatalog once from the Activity. Persist enable, character, emotion, manual-voice, and fallback choices through updateCoreSetting; retain drafts and explicit save for advanced values. Render service status, core choices, one stateful preview button, and a collapsed advanced section; place the HTTP warning beside the URL.

- [ ] Step 4: Verify GREEN

Run: ./gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.ui.SettingsViewModelTest --tests dev.breenottshook.ui.SettingsScreenTest

Expected: PASS.

- [ ] Step 5: Commit only Task 2 files

Run: git add -- app/src/main/java/dev/breenottshook/ui/MainActivity.kt app/src/main/java/dev/breenottshook/ui/SettingsViewModel.kt app/src/main/java/dev/breenottshook/ui/SettingsScreen.kt app/src/test/java/dev/breenottshook/ui/SettingsViewModelTest.kt app/src/test/java/dev/breenottshook/ui/SettingsScreenTest.kt; git commit -m "feat: automate core TTS setup"

### Task 3: Align the injected host page and remove the obsolete dialog shell

**Files:**
- Modify: app/src/main/java/dev/breenottshook/ui/host/HostSettingsDialog.kt
- Modify: app/src/main/java/dev/breenottshook/ui/host/HostFieldFactory.kt
- Create: app/src/test/java/dev/breenottshook/ui/host/HostSettingsDialogTest.kt
- Modify: docs/DIAGNOSTICS.md
- Modify: docs/INSTALL.md

**Interfaces:**
- Produces HostSettingsDialog.previewActionLabel(isPreviewing: Boolean): String.
- Produces HostFieldFactory.switchContentDescription(label: String, checked: Boolean): String.

- [ ] Step 1: Write failing host tests

    @Test
    fun hostSwitchAnnouncesLabelAndCurrentState() {
        assertEquals("启用第三方 TTS，已开启，双击切换",
            HostFieldFactory.switchContentDescription("启用第三方 TTS", true))
    }

    @Test
    fun hostPreviewUsesOneActionLabelForEachState() {
        assertEquals("试听", HostSettingsDialog.previewActionLabel(false))
        assertEquals("停止试听", HostSettingsDialog.previewActionLabel(true))
    }

- [ ] Step 2: Verify RED

Run: ./gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.ui.host.HostSettingsDialogTest

Expected: compilation failure because the helpers do not exist.

- [ ] Step 3: Implement host-native behavior

Replace the four-button panel with persistent service status, refresh/test actions, and one preview button. Move non-core fields below a clickable 高级设置 disclosure. Give switches label-plus-state accessibility text and 48dp touch rows. Confirm no production caller invokes HostSettingsDialog.show(), then remove that AlertDialog-only method and imports.

- [ ] Step 4: Verify GREEN and full regression suite

Run: powershell -ExecutionPolicy Bypass -File .\scripts\verify.ps1

Expected: all unit tests and lint pass, build succeeds, and output includes VERIFICATION_OK.

- [ ] Step 5: Device smoke test and commit

Verify on device that catalog loads automatically, connection state remains visible, preview is a single stateful action, core choices persist after reopening, and TalkBack announces switches correctly.

Run: git add -- app/src/main/java/dev/breenottshook/ui/host/HostSettingsDialog.kt app/src/main/java/dev/breenottshook/ui/host/HostFieldFactory.kt app/src/test/java/dev/breenottshook/ui/host/HostSettingsDialogTest.kt docs/DIAGNOSTICS.md docs/INSTALL.md; git commit -m "feat: automate host TTS settings"
