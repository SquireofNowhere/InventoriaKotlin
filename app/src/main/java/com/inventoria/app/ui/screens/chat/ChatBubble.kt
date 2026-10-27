package com.inventoria.app.ui.screens.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val BUBBLE_SIZE = 52.dp

/** Kept this far from every edge, so it is never flush against the screen's edge. */
private val EDGE_MARGIN = 8.dp
private const val PREFS = "chat_bubble"

/**
 * The chat button that floats over every tab and page, so Claude is one tap away from anywhere.
 * It can be dragged, and stays where it is left (as a share of the screen, so it survives rotation
 * and a different window size): every screen has buttons of its own in some corner, and which one
 * the bubble would sit on is up to whoever holds the phone. First time, it sits on the right edge,
 * below the middle.
 *
 * It fills its parent only to measure it; nothing but the bubble itself takes touches.
 */
@Composable
fun ChatBubbleOverlay(onOpenChat: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var fractionX by remember { mutableFloatStateOf(prefs.getFloat("x", 1f).coerceIn(0f, 1f)) }
    var fractionY by remember { mutableFloatStateOf(prefs.getFloat("y", 0.62f).coerceIn(0f, 1f)) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sizePx = with(density) { BUBBLE_SIZE.toPx() }
        val marginPx = with(density) { EDGE_MARGIN.toPx() }
        val maxX = (with(density) { maxWidth.toPx() } - sizePx - 2 * marginPx).coerceAtLeast(1f)
        val maxY = (with(density) { maxHeight.toPx() } - sizePx - 2 * marginPx).coerceAtLeast(1f)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset((marginPx + fractionX * maxX).roundToInt(), (marginPx + fractionY * maxY).roundToInt()) }
                .size(BUBBLE_SIZE)
                // The bubble lives at the edges, where the system's Back swipe also starts: without this,
                // dragging it in from the side goes Back instead of moving it.
                .systemGestureExclusion()
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = { prefs.edit().putFloat("x", fractionX).putFloat("y", fractionY).apply() }
                    ) { change, drag ->
                        change.consume()
                        fractionX = (fractionX + drag.x / maxX).coerceIn(0f, 1f)
                        fractionY = (fractionY + drag.y / maxY).coerceIn(0f, 1f)
                    }
                }
                .clickable(onClick = onOpenChat)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Chat with Claude",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
