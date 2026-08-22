package dev.breenottshook.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
    fun `initial screen shows one preview action and keeps advanced fields collapsed`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    persistedVersion = 7,
                    persisted = TtsConfig(),
                    draft = TtsConfig()
                ),
                onEdit = {},
                onUpdateCoreSetting = {},
                onSave = {},
                onRefreshCatalog = {},
                onTestConnection = {},
                onPreview = {},
                onStopPreview = {},
                onResetDefaults = {}
            )
        }

        composeRule.onNodeWithText("试听").assertIsDisplayed()
        composeRule.onAllNodesWithText("停止试听").assertCountEquals(0)
        composeRule.onAllNodesWithText("API 地址").assertCountEquals(0)
        composeRule.onNodeWithText("高级设置").assertIsDisplayed()
    }
}
