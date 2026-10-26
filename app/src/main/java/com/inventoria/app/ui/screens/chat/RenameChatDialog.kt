package com.inventoria.app.ui.screens.chat

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.inventoria.app.data.ChatRepository

/** Asks for a new name for a chat. Shared by the chat itself and the chat list. */
@Composable
fun RenameChatDialog(currentTitle: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(currentTitle) }
    val ready = title.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(ChatRepository.MAX_TITLE_LENGTH) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { onRename(title); onDismiss() }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
