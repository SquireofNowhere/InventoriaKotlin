package com.inventoria.app.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val PREFS_NAME = "timeline_zoom"
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 8f

/** The stops the zoom buttons step through; pinching is continuous between [MIN_ZOOM] and [MAX_ZOOM]. */
private val ZOOM_STEPS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f, 6f, 8f)

/**
 * How zoomed a timeline is: 1 is the scale it has always had, 2 makes an hour twice as tall. It is
 * only a number -- each timeline multiplies its own base hour height by it.
 */
@Stable
class TimelineZoomState(initial: Float) {
    var scale by mutableStateOf(initial.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set

    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun stepIn() {
        scale = ZOOM_STEPS.firstOrNull { it > scale + 0.01f } ?: MAX_ZOOM
    }

    fun stepOut() {
        scale = ZOOM_STEPS.lastOrNull { it < scale - 0.01f } ?: MIN_ZOOM
    }

    fun reset() {
        scale = 1f
    }
}

/** A [TimelineZoomState] that remembers its value across launches, under [key]. */
@Composable
fun rememberTimelineZoom(key: String): TimelineZoomState {
    val context = LocalContext.current
    val store = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val state = remember { TimelineZoomState(store.getFloat(key, 1f)) }
    // Written a moment after the last change, not on every frame of a pinch.
    LaunchedEffect(state) {
        snapshotFlow { state.scale }.collectLatest {
            delay(400)
            store.edit().putFloat(key, it).apply()
        }
    }
    return state
}

/**
 * Two-finger pinch, reported as the change in spread since the last event and the vertical
 * position, in this modifier's own coordinates, that the fingers are centred on. It runs on the
 * Initial pass, ahead of any scrolling below it, and only takes over while two fingers are down --
 * one finger still scrolls and taps as before.
 */
fun Modifier.pinchToZoom(onZoom: (factor: Float, focalY: Float) -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onZoom)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val factor = event.calculateZoom()
                    if (factor != 1f) {
                        current(factor, event.calculateCentroid().y)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * Minutes between gridlines at [hourHeightDp] dp per hour: hourly at the usual scale, then finer as
 * you zoom in, always keeping the lines far enough apart to read.
 */
fun tickMinutes(hourHeightDp: Float): Int = when {
    hourHeightDp >= 480f -> 5
    hourHeightDp >= 220f -> 15
    hourHeightDp >= 110f -> 30
    else -> 60
}

/** A small pill with zoom out, the current zoom (tap to return to 100%), and zoom in. */
@Composable
fun TimelineZoomControls(
    state: TimelineZoomState,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onZoomOut, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(20.dp))
            }
            Text(
                text = "${(state.scale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clickable(onClick = onReset)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )
            IconButton(onClick = onZoomIn, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(20.dp))
            }
        }
    }
}
