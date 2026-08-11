package dev.breenottshook.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun `screen renders complete sections warning and primary actions`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    persistedVersion = 7,
                    persisted = TtsConfig(),
                    draft = TtsConfig()
                ),
                onEdit = {},
                onSave = {},
                onRefreshCatalog = {},
                onTestConnection = {},
                onPreview = {},
                onStopPreview = {},
                onResetDefaults = {}
            )
        }

        listOf("基础", "音色", "高级生成", "调试").forEach {
            composeRule.onNodeWithText(it).fetchSemanticsNode()
        }
        composeRule.onNodeWithText("HTTP 连接未加密，请勿在不可信网络传输敏感文本。")
            .assertIsDisplayed()
        listOf("刷新音色", "测试连接", "试听", "保存配置").forEach {
            composeRule.onNodeWithText(it).fetchSemanticsNode()
        }
    }
}
