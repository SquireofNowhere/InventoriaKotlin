package com.inventoria.app.ui.screens.help.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.ui.screens.help.model.*
import com.inventoria.app.ui.theme.Success

/** Corner radius, stated explicitly rather than read from MaterialTheme.shapes: this app builds
 * its MaterialTheme without installing its own Shapes, so the theme value is the framework default
 * and would silently change if that were ever fixed. */
private val DiagramCorner = RoundedCornerShape(10.dp)

@Composable
private fun DiagramAccent.color(): Color = when (this) {
    DiagramAccent.Primary -> MaterialTheme.colorScheme.primary
    DiagramAccent.Success -> Success
    DiagramAccent.Warning -> Color(0xFFFB8C00)
    DiagramAccent.Danger -> MaterialTheme.colorScheme.error
    DiagramAccent.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    is DiagramAccent.Kind -> Color(kind.colorValue)
}

/**
 * Draws a spec.
 *
 * Deliberately no phone bezel: on a 360dp screen the frame is already narrow, and a drawn device
 * outline would spend a quarter of the width saying nothing. The whole frame reports itself to
 * accessibility as one sentence -- [caption] -- rather than letting TalkBack read twenty
 * disconnected fragments of a picture.
 */
@Composable
fun HelpDiagram(
    spec: DiagramSpec,
    caption: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = caption },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            // Generous padding, and nothing clips: callout badges hang outside their element's
            // bounds by design, and a tight frame would shave them off.
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                spec.elements.forEach { DiagramElementView(it) }
            }
        }
        if (spec.callouts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            spec.callouts.forEachIndexed { index, text ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    CalloutBadge(index + 1)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CalloutBadge(number: Int) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * The single highlight treatment, shared by every primitive so it can't drift: a primary ring
 * around the thing, plus a numbered badge when the diagram is numbering rather than highlighting.
 */
@Composable
private fun Highlighted(
    highlight: Boolean,
    callout: Int?,
    content: @Composable () -> Unit
) {
    if (!highlight && callout == null) {
        content()
        return
    }
    Box {
        Box(
            modifier = Modifier
                .border(2.dp, MaterialTheme.colorScheme.primary, DiagramCorner)
                .padding(2.dp)
        ) { content() }
        if (callout != null) {
            Box(modifier = Modifier.align(Alignment.TopStart)) { CalloutBadge(callout) }
        }
    }
}

@Composable
private fun DiagramElementView(element: DiagramElement) {
    when (element) {
        is DiagramElement.TopBar -> Highlighted(element.highlight, element.callout) { TopBarView(element) }
        is DiagramElement.Row -> Highlighted(element.highlight, element.callout) { RowView(element) }
        is DiagramElement.SectionHeader -> Text(
            text = element.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        is DiagramElement.SettingsRow -> Highlighted(element.highlight, element.callout) { SettingsRowView(element) }
        is DiagramElement.ChipRow -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            element.chips.forEach { ChipView(it) }
        }
        is DiagramElement.Fab -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) { Highlighted(element.highlight, element.callout) { FabView(element) } }
        is DiagramElement.Popup -> Highlighted(element.highlight, element.callout) { PopupView(element) }
        is DiagramElement.BottomNav -> BottomNavView(element)
        is DiagramElement.Note -> Text(
            text = element.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MiniSurface(
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Surface(shape = DiagramCorner, color = color, modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun TopBarView(bar: DiagramElement.TopBar) {
    MiniSurface(
        color = if (bar.style == TopBarStyle.Contextual) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bar.hasBackArrow) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = bar.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            bar.actions.forEach { icon ->
                Highlighted(icon.highlight, icon.callout) {
                    Icon(
                        icon.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).padding(horizontal = 1.dp),
                        tint = if (icon.highlight) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun RowView(row: DiagramElement.Row) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (row.indent > 0) Spacer(Modifier.width((row.indent * 14).dp))
        MiniSurface(
            color = if (row.selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.leadingBar?.let { accent ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent.color())
                    )
                    Spacer(Modifier.width(6.dp))
                }
                row.leadingIcon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    row.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    row.meta?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    row.gesture?.let {
                        Text(
                            text = it.describe(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                row.chips.forEach {
                    ChipView(it)
                    Spacer(Modifier.width(4.dp))
                }
                row.trailing.forEach { icon ->
                    Highlighted(icon.highlight, icon.callout) {
                        Icon(
                            icon.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (icon.highlight) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

private fun DiagramGesture.describe(): String = when (this) {
    DiagramGesture.Tap -> "▸ tap"
    DiagramGesture.LongPress -> "▸ press and hold"
    DiagramGesture.Drag -> "▸ drag"
    DiagramGesture.SwipeLeft -> "▸ swipe left"
    DiagramGesture.SwipeRight -> "▸ swipe right"
}

@Composable
private fun SettingsRowView(row: DiagramElement.SettingsRow) {
    MiniSurface {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            row.icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                row.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when (row.control) {
                SettingsControl.Switch -> Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary)
                    )
                }
                SettingsControl.Chevron -> Text(
                    "›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsControl.None -> Unit
            }
        }
    }
}

@Composable
private fun ChipView(chip: DiagramChip) {
    val accent = chip.accent.color()
    Highlighted(chip.highlight, chip.callout) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = accent.copy(alpha = 0.15f),
            // Kind colours include some very pale yellows that vanish as a 15% wash, so every chip
            // carries a border in its own accent rather than relying on the fill alone.
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chip.leadingDot) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(4.dp))
                }
                Text(chip.label, style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }
    }
}

@Composable
private fun FabView(fab: DiagramElement.Fab) {
    Surface(shape = if (fab.label == null) CircleShape else RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(fab.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            fab.label?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun PopupView(popup: DiagramElement.Popup) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (popup.style == PopupStyle.BottomSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
            Text(popup.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            popup.body?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            popup.fields.forEach { field ->
                Spacer(Modifier.height(6.dp))
                Highlighted(field.highlight, field.callout) { FieldView(field) }
            }
            if (popup.confirmLabel != null || popup.dismissLabel != null) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    popup.dismissLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                    }
                    popup.confirmLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldView(field: DiagramField) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(field.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (field.value.isNotBlank()) {
                    Text(field.value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            when (field.kind) {
                FieldKind.Dropdown -> Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FieldKind.Toggle -> Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                FieldKind.Stepper -> Text("- +", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FieldKind.Text -> Unit
            }
        }
    }
}

@Composable
private fun BottomNavView(nav: DiagramElement.BottomNav) {
    MiniSurface {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            nav.items.forEachIndexed { index, item ->
                val selected = index == nav.selectedIndex
                Icon(
                    item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (selected) 16.dp else 14.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
