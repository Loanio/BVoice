package dev.breenottshook.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.breenottshook.config.TtsConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `initial screen keeps expert controls collapsed behind one setup action`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    persistedVersion = 7,
                    persisted = TtsConfig(),
                    draft = TtsConfig()
                ),
                onEdit = {},
                onUpdateCoreSetting = {},
                onTestConnection = {},
                onPreview = {},
                onAddressBlur = {},
                onStopPreview = {},
                onResetDefaults = {}
            )
        }

        composeRule.onAllNodesWithText("点击卡片测试连接并刷新音色").assertCountEquals(1)
        composeRule.onAllNodesWithText("试听当前音色").assertCountEquals(1)
        composeRule.onAllNodesWithText("刷新音色").assertCountEquals(0)
        composeRule.onAllNodesWithText("测试连接").assertCountEquals(0)
        composeRule.onAllNodesWithText("试听").assertCountEquals(0)
        composeRule.onAllNodesWithText("API 地址").assertCountEquals(1)
        composeRule.onAllNodesWithText("使用手动音色").assertCountEquals(0)
        composeRule.onAllNodesWithText("高级设置").assertCountEquals(1)
    }

    @Test
    fun `busy state disables core toggles and voice pickers`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    persistedVersion = 7,
                    persisted = TtsConfig(),
                    draft = TtsConfig(
                        enabled = true,
                        character = "花火",
                        emotion = "开心"
                    ),
                    characters = listOf("花火"),
                    emotions = listOf("开心"),
                    operation = SettingsOperation.PREVIEWING
                ),
                onEdit = {},
                onUpdateCoreSetting = {},
                onTestConnection = {},
                onPreview = {},
                onAddressBlur = {},
                onStopPreview = {},
                onResetDefaults = {}
            )
        }

        composeRule.onNodeWithText("花火").assertIsNotEnabled()
        composeRule.onNodeWithText("开心").assertIsNotEnabled()
    }

    @Test
    fun `advanced settings exposes concurrent synthesis and playback interval inputs`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    persistedVersion = 7,
                    persisted = TtsConfig(),
                    draft = TtsConfig(maxConcurrentSynthesis = 12, playbackIntervalMs = 450)
                ),
                onEdit = {},
                onUpdateCoreSetting = {},
                onTestConnection = {},
                onPreview = {},
                onAddressBlur = {},
                onStopPreview = {},
                onResetDefaults = {}
            )
        }

        composeRule.onNodeWithText("高级设置").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("并发请求数量").performScrollTo().assertExists()
        composeRule.onNodeWithText("播放间隔（毫秒）").assertExists()
        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithText("450").assertExists()
    }

}
