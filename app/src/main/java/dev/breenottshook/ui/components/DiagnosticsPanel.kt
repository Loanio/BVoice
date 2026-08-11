package dev.breenottshook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsPanel(
    version: Long,
    message: String?,
    connectionSucceeded: Boolean?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("共享配置版本：$version", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Hook 能力将在小布进程加载后写入诊断状态；日志不会记录完整播报文本。",
            style = MaterialTheme.typography.bodySmall
        )
        connectionSucceeded?.let {
            Text(if (it) "最近连接测试：成功" else "最近连接测试：失败")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
