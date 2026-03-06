package com.inventoria.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun UnequipRepackDialog(
    itemName: String,
    containerName: String?,
    onDismiss: () -> Unit,
    onUnequipOnly: () -> Unit,
    onRepack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unequip $itemName") },
        text = { 
            val containerText = if (containerName != null) " back to $containerName" else " into its original container"
            Text("Would you like to repack this item$containerText or leave it at your current location?") 
        },
        confirmButton = {
            TextButton(onClick = onRepack) {
                Text("Repack")
            }
        },
        dismissButton = {
            TextButton(onClick = onUnequipOnly) {
                Text("Leave Here")
            }
        }
    )
}
