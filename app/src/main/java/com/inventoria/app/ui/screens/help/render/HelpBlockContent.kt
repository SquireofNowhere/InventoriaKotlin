package com.inventoria.app.ui.screens.help.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.ui.screens.help.diagram.HelpDiagram
import com.inventoria.app.ui.screens.help.model.CalloutKind
import com.inventoria.app.ui.screens.help.model.HelpBlock

/** Renders one content block. Kept separate from the article screen so the same blocks could later
 * appear elsewhere (a per-screen help sheet, say) without dragging a Scaffold along. */
@Composable
fun HelpBlockContent(block: HelpBlock, modifier: Modifier = Modifier) {
    when (block) {
        is HelpBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )

        is HelpBlock.Steps -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            block.steps.forEachIndexed { index, step ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = step.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // A step's diagram is indented under it so the association is unambiguous when
                    // several steps in a row each carry one.
                    step.diagram?.let { spec ->
                        Box(modifier = Modifier.padding(start = 32.dp)) {
                            HelpDiagram(spec = spec, caption = step.text)
                        }
                    }
                }
            }
        }

        is HelpBlock.Bullets -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            block.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }

        is HelpBlock.Definitions -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            block.terms.forEach { term ->
                Column {
                    Text(term.term, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        term.meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is HelpBlock.Callout -> {
            val (icon, accent) = when (block.kind) {
                CalloutKind.Tip -> Icons.Default.Lightbulb to MaterialTheme.colorScheme.primary
                CalloutKind.Caution -> Icons.Default.WarningAmber to MaterialTheme.colorScheme.error
                CalloutKind.Note -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.08f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Text(block.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }

        is HelpBlock.Diagram -> Column(modifier = modifier) {
            HelpDiagram(spec = block.spec, caption = block.caption)
            Spacer(Modifier.height(6.dp))
            Text(
                text = block.caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
