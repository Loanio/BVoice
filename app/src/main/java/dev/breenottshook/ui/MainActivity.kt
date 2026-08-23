package dev.breenottshook.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dev.breenottshook.api.CharacterCache
import dev.breenottshook.api.AndroidApiDiagnostics
import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.config.ConfigRepository
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val client = GptSovitsClient(OkHttpClient(), diagnostics = AndroidApiDiagnostics)
        val repository = ContentProviderSettingsRepository(ConfigRepository(contentResolver))
        val viewModel = SettingsOperationController(
            repository = repository,
            catalogGateway = ApiCatalogGateway(
                CharacterCache(loader = client::fetchCharacters)
            ),
            connectionTester = ApiConnectionTester(client),
            previewController = SessionPreviewController(this, lifecycleScope, client),
            operationScope = lifecycleScope
        )
        viewModel.initialize()

        setContent {
            val state by viewModel.state.collectAsState()
            LaunchedEffect(state.message) {
                state.message
                    ?.takeUnless {
                        it.startsWith("正在") ||
                            it == "高级设置会自动保存" ||
                            it.startsWith("已自动保存") ||
                            it.startsWith("试听失败")
                    }
                    ?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    onEdit = { next -> viewModel.edit { next } },
                    onUpdateCoreSetting = { next -> viewModel.updateCoreSetting { next } },
                    onTestConnection = viewModel::testConnectionAndRefresh,
                    onPreview = viewModel::preview,
                    onAddressBlur = viewModel::testConnectionAndRefresh,
                    onStopPreview = viewModel::stopPreview,
                    onResetDefaults = viewModel::resetDefaults,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    }
}
