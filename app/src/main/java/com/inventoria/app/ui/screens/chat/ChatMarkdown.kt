package com.inventoria.app.ui.screens.chat

/*
 * What Claude's replies are written in. They come back as markdown -- **bold**, lists, `code`,
 * fenced blocks, [links](...) -- and were shown as the raw characters. This is the parser; drawing
 * it is ChatMarkdownView. It is plain Kotlin with no Compose in it so every rule can be tested.
 *
 * It reads the markdown a chat reply actually uses and no more: headings, paragraphs, bullet and
 * numbered lists (nested by indentation), quotes, fenced code, rules and tables, with bold, italic,
 * inline code and links inside the text. Anything it does not recognise is left as the plain text it
 * was, so a reply is never lost to a parsing rule. A reply is parsed again each time it changes, so
 * a half-finished construct (an unclosed fence) has to come out sensibly too.
 */

/** One run of inline text and how it is dressed. [link] is the target when it is a link. */
internal data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null
)

internal data class ListItem(val depth: Int, val text: String)

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullets(val items: List<ListItem>) : MdBlock
    data class Numbered(val start: Int, val items: List<ListItem>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("""^(#{1,6})\s+(.*?)(?:\s+#+)?\s*$""")
private val RULE = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
private val BULLET = Regex("""^(\s*)[-*+•]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d{1,9})[.)]\s+(.*)$""")
private val QUOTE = Regex("""^\s{0,3}>\s?(.*)$""")
private val FENCE = Regex("""^\s{0,3}(```+|~~~+)\s*([\w+#.-]*)\s*$""")
private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

/** Two spaces of indent make one level of nesting, a tab counts as two levels' worth of four. */
private fun depthOf(indent: String): Int = indent.replace("\t", "    ").length / 2

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun startsBlock(line: String, next: String?): Boolean =
    line.isBlank() || HEADING.matches(line) || RULE.matches(line) || FENCE.matches(line) ||
        QUOTE.matches(line) || BULLET.matches(line) || NUMBERED.matches(line) ||
        (line.contains('|') && next != null && TABLE_SEPARATOR.matches(next) && next.contains('|'))

internal fun parseMarkdown(markdown: String): List<MdBlock> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1)

        val fence = FENCE.matchEntire(line)
        if (fence != null) {
            val marker = fence.groupValues[1]
            val body = mutableListOf<String>()
            i++
            // An unclosed fence runs to the end: the reply may still be arriving.
            while (i < lines.size && !lines[i].trim().startsWith(marker.take(3))) {
                body += lines[i]
                i++
            }
            i++ // the closing fence
            blocks += MdBlock.Code(fence.groupValues[2], body.joinToString("\n"))
            continue
        }

        if (line.isBlank()) { i++; continue }

        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            i++
            continue
        }

        if (RULE.matches(line)) { blocks += MdBlock.Rule; i++; continue }

        if (QUOTE.matches(line)) {
            val body = mutableListOf<String>()
            while (i < lines.size) {
                val quote = QUOTE.matchEntire(lines[i]) ?: break
                body += quote.groupValues[1]
                i++
            }
            blocks += MdBlock.Quote(body.joinToString("\n").trim())
            continue
        }

        if (line.contains('|') && next != null && next.contains('|') && TABLE_SEPARATOR.matches(next)) {
            val header = tableCells(line)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                rows += tableCells(lines[i])
                i++
            }
            blocks += MdBlock.Table(header, rows)
            continue
        }

        if (BULLET.matches(line)) {
            val items = mutableListOf<ListItem>()
            while (i < lines.size) {
                val m = BULLET.matchEntire(lines[i]) ?: break
                items += ListItem(depthOf(m.groupValues[1]), m.groupValues[2])
                i++
            }
            blocks += MdBlock.Bullets(items)
            continue
        }

        val numbered = NUMBERED.matchEntire(line)
        if (numbered != null) {
            val start = numbered.groupValues[2].toIntOrNull() ?: 1
            val items = mutableListOf<ListItem>()
            while (i < lines.size) {
                val m = NUMBERED.matchEntire(lines[i]) ?: break
                items += ListItem(depthOf(m.groupValues[1]), m.groupValues[3])
                i++
            }
            blocks += MdBlock.Numbered(start, items)
            continue
        }

        // A paragraph: this line and every following one that does not start something else.
        // The line breaks stay, because a chat reply uses them on purpose.
        val body = mutableListOf(line.trimEnd())
        i++
        while (i < lines.size && !startsBlock(lines[i], lines.getOrNull(i + 1))) {
            body += lines[i].trimEnd()
            i++
        }
        blocks += MdBlock.Paragraph(body.joinToString("\n"))
    }
    return blocks
}

// --- Inline -----------------------------------------------------------------------------------

private val BARE_URL = Regex("""(?:https?|inventoria)://[^\s<>()\[\]]+[^\s<>()\[\].,;:!?'"]""")

private fun isWordChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_')

/**
 * The runs of [text]. Styles nest (bold around italic, either around a link's label); an opener with
 * no closer is literal text, so `2 * 3` and a stray backtick come through untouched.
 */
internal fun parseInline(text: String): List<InlineRun> {
    val runs = mutableListOf<InlineRun>()
    parseInlineInto(text, InlineRun(""), runs)
    return runs.fold(mutableListOf()) { merged, run ->
        val last = merged.lastOrNull()
        if (last != null && last.copy(text = "") == run.copy(text = "")) {
            merged[merged.lastIndex] = last.copy(text = last.text + run.text)
        } else if (run.text.isNotEmpty()) {
            merged += run
        }
        merged
    }
}

private fun parseInlineInto(text: String, style: InlineRun, out: MutableList<InlineRun>) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            out += style.copy(text = plain.toString())
            plain.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]

        // A backslash makes the next punctuation mark literal.
        if (c == '\\' && i + 1 < text.length && text[i + 1] in "\\`*_{}[]()#+-.!|>~") {
            plain.append(text[i + 1])
            i += 2
            continue
        }

        if (c == '`') {
            var ticks = 1
            while (i + ticks < text.length && text[i + ticks] == '`') ticks++
            val fence = "`".repeat(ticks)
            val close = text.indexOf(fence, i + ticks)
            if (close > i + ticks) {
                flush()
                out += style.copy(text = text.substring(i + ticks, close).trim(), code = true)
                i = close + ticks
                continue
            }
            plain.append(fence)
            i += ticks
            continue
        }

        if (c == '[') {
            val labelEnd = findClosingBracket(text, i)
            if (labelEnd != -1 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                val urlEnd = findClosingParen(text, labelEnd + 1)
                if (urlEnd != -1) {
                    val url = text.substring(labelEnd + 2, urlEnd).trim().substringBefore(' ')
                    if (url.isNotEmpty()) {
                        flush()
                        parseInlineInto(text.substring(i + 1, labelEnd), style.copy(link = url), out)
                        i = urlEnd + 1
                        continue
                    }
                }
            }
        }

        if (c == '*' || c == '_') {
            var run = 1
            while (i + run < text.length && text[i + run] == c) run++
            val marks = minOf(run, 3)
            val closed = tryEmphasis(text, i, c, marks, style, out, ::flush)
            if (closed != -1) {
                i = closed
                continue
            }
            plain.append(c.toString().repeat(run))
            i += run
            continue
        }

        if ((c == 'h' || c == 'i') && style.link == null && !style.code) {
            val url = BARE_URL.matchAt(text, i)
            if (url != null && (i == 0 || !isWordChar(text[i - 1]))) {
                val appLink = parseChatLink(url.value)
                if (url.value.startsWith(CHAT_LINK_SCHEME, ignoreCase = true)) {
                    // A bare address to something in the app: Claude was asked for a markdown link but
                    // wrote the address on its own. Show it as the same button, with a plain label
                    // rather than the long id. An address the app cannot open is just text.
                    if (appLink != null) {
                        flush()
                        out += style.copy(text = chatLinkLabel(appLink), link = url.value)
                        i += url.value.length
                        continue
                    }
                } else {
                    flush()
                    out += style.copy(text = url.value, link = url.value)
                    i += url.value.length
                    continue
                }
            }
        }

        plain.append(c)
        i++
    }
    flush()
}

private fun findClosingBracket(text: String, open: Int): Int {
    var depth = 0
    var j = open
    while (j < text.length) {
        when (text[j]) {
            '\\' -> j++ // skip the escaped character
            '[' -> depth++
            ']' -> { depth--; if (depth == 0) return j }
        }
        j++
    }
    return -1
}

private fun findClosingParen(text: String, open: Int): Int {
    var depth = 0
    for (j in open until text.length) {
        when (text[j]) {
            '(' -> depth++
            ')' -> { depth--; if (depth == 0) return j }
        }
    }
    return -1
}

/**
 * Emphasis opened at [start] with [marks] copies of [mark]: returns the index after it, having
 * written the runs, or -1 when there is nothing to close it. `_` only emphasises at a word edge, so
 * snake_case stays snake_case.
 */
private fun tryEmphasis(
    text: String,
    start: Int,
    mark: Char,
    marks: Int,
    style: InlineRun,
    out: MutableList<InlineRun>,
    flush: () -> Unit
): Int {
    val opener = start + marks
    if (opener >= text.length || text[opener].isWhitespace()) return -1
    if (mark == '_' && isWordChar(text.getOrNull(start - 1))) return -1

    val closer = mark.toString().repeat(marks)
    var search = opener
    while (true) {
        val at = text.indexOf(closer, search)
        if (at == -1 || at == opener) return -1
        val before = text[at - 1]
        val after = text.getOrNull(at + marks)
        // A closer is preceded by text, not space, and (for _) is not followed by more word.
        val valid = !before.isWhitespace() && !(mark == '_' && isWordChar(after)) && after != mark
        if (valid) {
            flush()
            val inner = style.copy(bold = style.bold || marks >= 2, italic = style.italic || marks == 1 || marks == 3)
            parseInlineInto(text.substring(opener, at), inner, out)
            return at + marks
        }
        search = at + 1
    }
}

// --- Links to things in the app ---------------------------------------------------------------

/**
 * Something in the app a reply can point at, written `inventoria://<kind>/<id>`. The MCP server
 * gives every todo, item, collection, tracked task and schedule block a ready-made `link` of this
 * shape, and the chat is told to use them, so "here are your todos" can be buttons that open them.
 */
sealed interface ChatLink {
    data class Todo(val id: String) : ChatLink
    data class Item(val id: Long) : ChatLink
    data class Collection(val id: Long) : ChatLink
    data class Task(val id: String) : ChatLink
    data class Block(val id: String) : ChatLink
}

internal const val CHAT_LINK_SCHEME = "inventoria://"

/** The thing [url] points at, or null for any other address (which opens in the browser). */
internal fun parseChatLink(url: String): ChatLink? {
    if (!url.startsWith(CHAT_LINK_SCHEME, ignoreCase = true)) return null
    val rest = url.substring(CHAT_LINK_SCHEME.length).trim('/')
    val kind = rest.substringBefore('/', "").lowercase()
    val id = rest.substringAfter('/', "").substringBefore('?').substringBefore('#').trim()
    if (id.isEmpty()) return null
    return when (kind) {
        "todo" -> ChatLink.Todo(id)
        "item" -> id.toLongOrNull()?.let(ChatLink::Item)
        "collection" -> id.toLongOrNull()?.let(ChatLink::Collection)
        "task", "timer" -> ChatLink.Task(id)
        "block", "schedule" -> ChatLink.Block(id)
        else -> null
    }
}

/** What a bare app address is shown as: it has no label of its own, and the id is not for reading. */
internal fun chatLinkLabel(link: ChatLink): String = when (link) {
    is ChatLink.Todo -> "Open todo"
    is ChatLink.Item -> "Open item"
    is ChatLink.Collection -> "Open collection"
    is ChatLink.Task -> "Open task"
    is ChatLink.Block -> "Open block"
}
