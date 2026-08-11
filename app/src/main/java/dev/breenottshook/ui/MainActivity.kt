package dev.breenottshook.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dev.breenottshook.api.CharacterCache
import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.config.ConfigRepository
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val client = GptSovitsClient(OkHttpClient())
        val repository = ContentProviderSettingsRepository(ConfigRepository(contentResolver))
        val viewModel = SettingsViewModel(
            repository = repository,
            catalogGateway = ApiCatalogGateway(
                CharacterCache(loader = client::fetchCharacters)
            ),
            connectionTester = ApiConnectionTester(client),
            previewController = SessionPreviewController(this, lifecycleScope, client)
        )

        setContent {
            val state by viewModel.state.collectAsState()
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    onEdit = { next -> viewModel.edit { next } },
                    onSave = viewModel::save,
                    onRefreshCatalog = viewModel::refreshCatalog,
                    onTestConnection = viewModel::testConnection,
                    onPreview = viewModel::preview,
                    onStopPreview = viewModel::stopPreview,
                    onResetDefaults = viewModel::resetDefaults,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    }
}
