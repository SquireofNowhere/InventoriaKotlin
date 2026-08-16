package com.inventoria.app.ui.screens.help.diagram

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*
import com.inventoria.app.ui.theme.InventoriaTheme

/**
 * Every diagram primitive in one place, in both themes.
 *
 * These exist because a diagram is hand-drawn from primitives and there is no other way to check
 * one short of deploying the app. Adding a new element to the vocabulary means adding it here --
 * a primitive nobody has looked at in dark mode is a primitive that is wrong in dark mode.
 */
private val kitchenSink = DiagramSpec(
    elements = listOf(
        DiagramElement.TopBar(
            title = "Tasks",
            actions = listOf(
                DiagramIcon(Icons.Default.BarChart),
                DiagramIcon(Icons.Default.History, highlight = true)
            )
        ),
        DiagramElement.SectionHeader("Recent Sessions"),
        DiagramElement.Row(
            title = "Writing",
            subtitle = "◈ Work",
            meta = "1h 12m • +216 pts",
            leadingBar = DiagramAccent.Kind(TaskKind.PEACOCK),
            chips = listOf(DiagramChip("Peak Performance", DiagramAccent.Kind(TaskKind.PEACOCK), leadingDot = true)),
            trailing = listOf(DiagramIcon(Icons.Default.Delete)),
            gesture = DiagramGesture.LongPress
        ),
        DiagramElement.Row(
            title = "Get Water",
            subtitle = "Interrupting Writing",
            leadingBar = DiagramAccent.Kind(TaskKind.BLUEBERRY),
            indent = 1,
            selected = true
        ),
        // Banana is the palest Kind colour and the one most likely to disappear -- it is in the
        // sink deliberately as the contrast canary.
        DiagramElement.ChipRow(
            listOf(
                DiagramChip("Banana", DiagramAccent.Kind(TaskKind.BANANA), leadingDot = true),
                DiagramChip("Danger", DiagramAccent.Danger),
                DiagramChip("Success", DiagramAccent.Success, highlight = true)
            )
        ),
        DiagramElement.SettingsRow(
            title = "Flow Mode",
            subtitle = "Start the next task automatically",
            control = SettingsControl.Switch,
            highlight = true
        ),
        DiagramElement.Popup(
            title = "Change Kind to Peak Performance",
            body = "\"Writing\" covers 3 sittings.",
            fields = listOf(
                DiagramField("Session Name", "Writing", FieldKind.Text),
                DiagramField("Session Category", "Peak Performance", FieldKind.Dropdown, highlight = true)
            ),
            confirmLabel = "Change all 3",
            dismissLabel = "Just this one"
        ),
        DiagramElement.Fab(Icons.Default.Add, highlight = true),
        DiagramElement.Note("Every primitive, drawn once.")
    ),
    callouts = listOf("A numbered callout, captioned beneath the frame.")
)

@Composable
private fun PreviewBody(dark: Boolean) {
    InventoriaTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HelpDiagram(spec = kitchenSink, caption = "All diagram primitives")
            }
        }
    }
}

@Preview(name = "Diagram primitives - light", showBackground = true, widthDp = 380)
@Composable
private fun DiagramPrimitivesLight() = PreviewBody(dark = false)

@Preview(
    name = "Diagram primitives - dark",
    showBackground = true,
    widthDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DiagramPrimitivesDark() = PreviewBody(dark = true)
