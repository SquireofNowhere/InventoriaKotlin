package com.inventoria.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.repository.SyncStatus
import com.inventoria.app.ui.theme.Success

/**
 * Sync state as a single top-bar glyph, sized to sit alongside the other action icons.
 *
 * Idle draws nothing at all. It used to draw a bare grey dot, which -- because this was wired into
 * the *title* slot rather than the actions slot -- meant the home screen's app bar was an anonymous
 * dot most of the time. There is nothing to tell the user when a sync isn't happening, so the
 * honest rendering is an empty slot.
 *
 * Icon-only, no caption: it lives in an actions row now, where a "Syncing..." label would push the
 * real actions around every time the state changed. The state is carried by contentDescription so
 * TalkBack still announces it.
 */
@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    if (syncStatus is SyncStatus.Idle) return

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val iconModifier = modifier
        .padding(horizontal = 4.dp)
        .size(20.dp)

    when (syncStatus) {
        is SyncStatus.Syncing -> Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Syncing",
            modifier = iconModifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.primary
        )
        is SyncStatus.Synced -> Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = "Synced",
            modifier = iconModifier,
            tint = Success
        )
        is SyncStatus.Error -> Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Sync error",
            modifier = iconModifier,
            tint = MaterialTheme.colorScheme.error
        )
        is SyncStatus.Idle -> Unit // unreachable, handled above
    }
}
