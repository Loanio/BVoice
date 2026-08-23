# Breeno Native Settings Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the floating settings button with a native-looking third-party voice preference immediately after Breeno Voice.

**Architecture:** A small immutable entry descriptor carries the stable key, title, default summary and insertion order. `BreenoSettingsHook` uses the host's `PreferenceScreen` and `androidx.preference.Preference` through reflection, so the existing RecyclerView adapter renders the row with Breeno's native theme.

**Tech Stack:** Kotlin, YukiHookAPI, AndroidX Preference supplied by the target app, JUnit.

## Global Constraints

- Insert only in `com.heytap.speechassist` settings.
- Use key `dev.breenottshook.preference.third_party_voice`.
- Place the row after the title `小布音色`.
- Show `点击配置` until a configured voice is available.
- Do not hand-build a duplicate Preference row or leave a decor overlay.

---

### Task 1: Describe native entry semantics

**Files:**
- Create: `app/src/main/java/dev/breenottshook/hook/SettingsPreferenceEntry.kt`
- Modify: `app/src/test/java/dev/breenottshook/hook/SettingsHostSelectorTest.kt`

**Interfaces:**
- Produces: `SettingsPreferenceEntry.orderAfter(anchorOrder: Int): Int`.

- [ ] **Step 1: Write the failing test**

```kotlin
assertEquals("第三方音色", SettingsPreferenceEntry.title)
assertEquals("点击配置", SettingsPreferenceEntry.defaultSummary)
assertEquals(42, SettingsPreferenceEntry.orderAfter(41))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.hook.SettingsHostSelectorTest`

- [ ] **Step 3: Write minimal implementation**

```kotlin
object SettingsPreferenceEntry {
    const val key = "dev.breenottshook.preference.third_party_voice"
    const val title = "第三方音色"
    const val defaultSummary = "点击配置"
    fun orderAfter(anchorOrder: Int) = anchorOrder + 1
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.hook.SettingsHostSelectorTest`

### Task 2: Insert native preference and remove overlay

**Files:**
- Modify: `app/src/main/java/dev/breenottshook/hook/BreenoSettingsHook.kt`

**Interfaces:**
- Consumes: `SettingsPreferenceEntry`.
- Produces: exactly one host preference rendered by the host RecyclerView.

- [ ] **Step 1: Hook Fragment view creation and read its PreferenceScreen**
- [ ] **Step 2: Find the host preference titled `小布音色`, create `androidx.preference.Preference`, then insert it with order anchor + 1**
- [ ] **Step 3: Bind the native preference click to `HostSettingsPage`**
- [ ] **Step 4: Run the focused unit test and assemble debug APK**

### Task 3: Device verification

**Files:**
- No source files.

- [ ] **Step 1: Install `app-debug.apk` with ADB**
- [ ] **Step 2: Reload the module in Vector and reopen settings**
- [ ] **Step 3: UI dump must show `个性化设置` → `小布音色` → `第三方音色`, with no `android.widget.Button` named `第三方音色`**
