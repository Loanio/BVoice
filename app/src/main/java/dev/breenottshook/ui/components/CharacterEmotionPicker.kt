package dev.breenottshook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChoicePicker(
    label: String,
    value: String,
    choices: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value.ifBlank { "请选择" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = {
                        expanded = false
                        onValueChange(choice)
                    }
                )
            }
            if (choices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无可用选项") },
                    onClick = { expanded = false },
                    enabled = false
                )
            }
        }
    }
}

@Composable
fun CharacterEmotionPicker(
    character: String,
    emotion: String,
    characters: List<String>,
    emotions: List<String>,
    onCharacterChange: (String) -> Unit,
    onEmotionChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ChoicePicker("角色", character, characters, onCharacterChange)
        ChoicePicker(
            label = "情感",
            value = emotion,
            choices = emotions,
            onValueChange = onEmotionChange,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
