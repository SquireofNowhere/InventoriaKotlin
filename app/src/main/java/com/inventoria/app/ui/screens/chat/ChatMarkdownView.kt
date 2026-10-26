package com.inventoria.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A reply from Claude, drawn from its markdown: headings, lists, quotes, code and tables, with bold,
 * italic, code and links inside the text. Links to things in the app (`inventoria://todo/<id>` and
 * the like) are drawn as little buttons and call [onLink]; any other address opens in the browser.
 *
 * The caller wraps this in a SelectionContainer, so everything on it can be selected and copied.
 */
@Composable
internal fun ChatMarkdownText(
    markdown: String,
    onLink: (ChatLink) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val colors = MarkdownColors(
        text = MaterialTheme.colorScheme.onSurface,
        link = MaterialTheme.colorScheme.primary,
        button = MaterialTheme.colorScheme.primaryContainer,
        buttonText = MaterialTheme.colorScheme.onPrimaryContainer,
        codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        rule = MaterialTheme.colorScheme.outlineVariant,
        muted = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp, color = colors.text)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(inline(block.text, colors, onLink), style = body)

                is MdBlock.Heading -> Text(
                    inline(block.text, colors, onLink),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(fontWeight = FontWeight.Bold, color = colors.text),
                    modifier = Modifier.padding(top = 6.dp)
                )

                is MdBlock.Bullets -> ListBlock(block.items, body, colors, onLink) { _, depth -> if (depth == 0) "•" else "◦" }

                is MdBlock.Numbered -> ListBlock(block.items, body, colors, onLink) { index, _ -> "${block.start + index}." }

                is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(colors.rule, RoundedCornerShape(2.dp))
                    )
                    Text(
                        inline(block.text, colors, onLink),
                        style = body.copy(color = colors.muted),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                is MdBlock.Code -> CodeBlock(block.text, colors)

                is MdBlock.Table -> CodeBlock(tableAsText(block), colors)

                MdBlock.Rule -> HorizontalDivider(color = colors.rule, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private class MarkdownColors(
    val text: Color,
    val link: Color,
    val button: Color,
    val buttonText: Color,
    val codeBackground: Color,
    val rule: Color,
    val muted: Color
)

@Composable
private fun ListBlock(
    items: List<ListItem>,
    body: TextStyle,
    colors: MarkdownColors,
    onLink: (ChatLink) -> Unit,
    marker: (index: Int, depth: Int) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(start = (item.depth * 16).dp)) {
                Text(marker(index, item.depth), style = body, modifier = Modifier.width(24.dp))
                Text(inline(item.text, colors, onLink), style = body, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String, colors: MarkdownColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.codeBackground, RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.text),
            softWrap = false
        )
    }
}

/** A table as aligned monospace text: columns padded to their widest cell, under a rule. */
private fun tableAsText(table: MdBlock.Table): String {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    fun row(cells: List<String>) = List(columns) { cells.getOrElse(it) { "" } }
    val all = listOf(row(table.header)) + table.rows.map(::row)
    val widths = List(columns) { column -> all.maxOf { it[column].length } }
    fun render(cells: List<String>) = cells.indices.joinToString("  ") { cells[it].padEnd(widths[it]) }.trimEnd()
    return buildString {
        appendLine(render(all.first()))
        appendLine(widths.joinToString("  ") { "─".repeat(it) })
        all.drop(1).forEach { appendLine(render(it)) }
    }.trimEnd()
}

/** The runs of [text] as one styled string, with its links wired to [onLink] or the browser. */
@Composable
private fun inline(text: String, colors: MarkdownColors, onLink: (ChatLink) -> Unit): AnnotatedString =
    remember(text, colors.link, colors.button, colors.buttonText, colors.codeBackground, onLink) {
        buildAnnotatedString {
            parseInline(text).forEach { run ->
                val style = SpanStyle(
                    fontWeight = if (run.bold) FontWeight.Bold else null,
                    fontStyle = if (run.italic) FontStyle.Italic else null,
                    fontFamily = if (run.code) FontFamily.Monospace else null,
                    fontSize = if (run.code) 0.9.em else androidx.compose.ui.unit.TextUnit.Unspecified,
                    background = if (run.code) colors.codeBackground else Color.Unspecified
                )
                val url = run.link
                if (url == null) {
                    withStyle(style) { append(run.text) }
                } else {
                    val target = parseChatLink(url)
                    if (target != null) {
                        // A thing in the app: a little button, not an address.
                        val look = TextLinkStyles(
                            SpanStyle(color = colors.buttonText, background = colors.button, fontWeight = FontWeight.Medium)
                        )
                        withLink(LinkAnnotation.Clickable(url, look) { onLink(target) }) {
                            withStyle(style) { append(" ${run.text} ") }
                        }
                    } else {
                        val look = TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))
                        withLink(LinkAnnotation.Url(url, look)) { withStyle(style) { append(run.text) } }
                    }
                }
            }
        }
    }
