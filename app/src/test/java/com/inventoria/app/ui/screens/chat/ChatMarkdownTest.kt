package com.inventoria.app.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {

    // --- blocks ---

    @Test
    fun `plain text is one paragraph and keeps its line breaks`() {
        assertEquals(listOf(MdBlock.Paragraph("one\ntwo")), parseMarkdown("one\ntwo"))
        assertEquals(
            listOf(MdBlock.Paragraph("one"), MdBlock.Paragraph("two")),
            parseMarkdown("one\n\ntwo")
        )
    }

    @Test
    fun `an empty reply has no blocks`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("  \n \n").isEmpty())
    }

    @Test
    fun `headings take their level and drop the hashes`() {
        assertEquals(
            listOf(MdBlock.Heading(1, "Title"), MdBlock.Heading(3, "Sub")),
            parseMarkdown("# Title\n### Sub ###")
        )
        // A hash that belongs to the text stays.
        assertEquals(listOf(MdBlock.Heading(2, "Learning C#")), parseMarkdown("## Learning C#"))
        // No space after the hashes: not a heading.
        assertEquals(listOf(MdBlock.Paragraph("#hashtag")), parseMarkdown("#hashtag"))
    }

    @Test
    fun `bullets are collected into one list with their nesting`() {
        val blocks = parseMarkdown("- a\n- b\n  - c\n* d")
        assertEquals(
            listOf(MdBlock.Bullets(listOf(ListItem(0, "a"), ListItem(0, "b"), ListItem(1, "c"), ListItem(0, "d")))),
            blocks
        )
    }

    @Test
    fun `a numbered list remembers where it starts`() {
        assertEquals(
            listOf(MdBlock.Numbered(3, listOf(ListItem(0, "three"), ListItem(0, "four")))),
            parseMarkdown("3. three\n4) four")
        )
    }

    @Test
    fun `bold text at the start of a line is not a bullet`() {
        assertEquals(listOf(MdBlock.Paragraph("**Note:** hello")), parseMarkdown("**Note:** hello"))
    }

    @Test
    fun `fenced code is kept as it was written`() {
        val blocks = parseMarkdown("before\n```kotlin\nval x = *1*\n\n- not a list\n```\nafter")
        assertEquals(
            listOf(
                MdBlock.Paragraph("before"),
                MdBlock.Code("kotlin", "val x = *1*\n\n- not a list"),
                MdBlock.Paragraph("after")
            ),
            blocks
        )
    }

    @Test
    fun `a fence that has not closed yet runs to the end`() {
        assertEquals(listOf(MdBlock.Code("", "partial\ncode")), parseMarkdown("```\npartial\ncode"))
    }

    @Test
    fun `quotes and rules`() {
        assertEquals(
            listOf(MdBlock.Quote("one\ntwo"), MdBlock.Rule, MdBlock.Paragraph("after")),
            parseMarkdown("> one\n> two\n\n---\nafter")
        )
    }

    @Test
    fun `a table is its header and rows`() {
        val table = parseMarkdown("| Item | Qty |\n|---|---|\n| Pens | 3 |\n| Ink | 1 |")
        assertEquals(listOf(MdBlock.Table(listOf("Item", "Qty"), listOf(listOf("Pens", "3"), listOf("Ink", "1")))), table)
    }

    @Test
    fun `a paragraph ends where a list begins`() {
        assertEquals(
            listOf(MdBlock.Paragraph("Here you go:"), MdBlock.Bullets(listOf(ListItem(0, "x")))),
            parseMarkdown("Here you go:\n- x")
        )
    }

    @Test
    fun `windows line endings read the same`() {
        assertEquals(parseMarkdown("a\n\n- b"), parseMarkdown("a\r\n\r\n- b"))
    }

    // --- inline ---

    @Test
    fun `plain text is one run`() {
        assertEquals(listOf(InlineRun("hello world")), parseInline("hello world"))
    }

    @Test
    fun `bold italic and code`() {
        assertEquals(
            listOf(
                InlineRun("a "), InlineRun("bold", bold = true), InlineRun(" and "),
                InlineRun("slanted", italic = true), InlineRun(" and "), InlineRun("code", code = true)
            ),
            parseInline("a **bold** and *slanted* and `code`")
        )
    }

    @Test
    fun `styles nest`() {
        assertEquals(
            listOf(InlineRun("a "), InlineRun("very ", bold = true), InlineRun("big", bold = true, italic = true)),
            parseInline("a **very *big***")
        )
    }

    @Test
    fun `text inside code is left alone`() {
        assertEquals(listOf(InlineRun("**x**", code = true)), parseInline("`**x**`"))
    }

    @Test
    fun `an opener with no closer is just text`() {
        assertEquals(listOf(InlineRun("2 * 3 = 6")), parseInline("2 * 3 = 6"))
        assertEquals(listOf(InlineRun("a ** b")), parseInline("a ** b"))
        assertEquals(listOf(InlineRun("a `b")), parseInline("a `b"))
    }

    @Test
    fun `underscores inside a word do not italicise`() {
        assertEquals(listOf(InlineRun("snake_case_name")), parseInline("snake_case_name"))
        assertEquals(listOf(InlineRun("an "), InlineRun("italic", italic = true), InlineRun(" word")), parseInline("an _italic_ word"))
    }

    @Test
    fun `an escaped mark is literal`() {
        assertEquals(listOf(InlineRun("*not bold*")), parseInline("\\*not bold\\*"))
    }

    @Test
    fun `links keep their label and target`() {
        assertEquals(
            listOf(InlineRun("see "), InlineRun("the site", link = "https://example.com/a"), InlineRun(".")),
            parseInline("see [the site](https://example.com/a).")
        )
    }

    @Test
    fun `a link label can be styled`() {
        assertEquals(
            listOf(InlineRun("Buy milk", bold = true, link = "inventoria://todo/abc")),
            parseInline("[**Buy milk**](inventoria://todo/abc)")
        )
    }

    @Test
    fun `a bare address becomes a link without swallowing the full stop`() {
        assertEquals(
            listOf(InlineRun("go to "), InlineRun("https://example.com/x", link = "https://example.com/x"), InlineRun(".")),
            parseInline("go to https://example.com/x.")
        )
    }

    @Test
    fun `a bare app address becomes a labelled button, not a long id`() {
        assertEquals(
            listOf(InlineRun("Task: "), InlineRun("Open task", link = "inventoria://task/efd72dd7-1950-4889")),
            parseInline("Task: inventoria://task/efd72dd7-1950-4889")
        )
        assertEquals(
            listOf(InlineRun("see "), InlineRun("Open item", link = "inventoria://item/1791183040129"), InlineRun(".")),
            parseInline("see inventoria://item/1791183040129.")
        )
        // every kind has a label, and the schedule alias Claude used reads as a block
        assertEquals("Open block", parseInline("inventoria://schedule/e13f").single().text)
        assertEquals("Open todo", parseInline("inventoria://todo/ced6").single().text)
        assertEquals("Open collection", parseInline("inventoria://collection/1").single().text)
    }

    @Test
    fun `a bare app address the app cannot open stays plain text`() {
        assertEquals(listOf(InlineRun("inventoria://item/notanumber")), parseInline("inventoria://item/notanumber"))
        assertEquals(listOf(InlineRun("inventoria://nothing/1")), parseInline("inventoria://nothing/1"))
        // and a word that merely contains the letters is untouched
        assertEquals(listOf(InlineRun("an inventoria is a list")), parseInline("an inventoria is a list"))
    }

    @Test
    fun `an address inside code is not turned into a button`() {
        assertEquals(listOf(InlineRun("inventoria://task/x", code = true)), parseInline("`inventoria://task/x`"))
        assertEquals(
            listOf(MdBlock.Code("", "inventoria://task/example")),
            parseMarkdown("```\ninventoria://task/example\n```")
        )
    }

    @Test
    fun `brackets that are not a link stay`() {
        assertEquals(listOf(InlineRun("[a] and (b)")), parseInline("[a] and (b)"))
    }

    // --- links into the app ---

    @Test
    fun `app links name what they point at`() {
        assertEquals(ChatLink.Todo("a-b-c"), parseChatLink("inventoria://todo/a-b-c"))
        assertEquals(ChatLink.Item(42), parseChatLink("inventoria://item/42"))
        assertEquals(ChatLink.Collection(7), parseChatLink("inventoria://collection/7"))
        assertEquals(ChatLink.Task("t1"), parseChatLink("inventoria://task/t1"))
        assertEquals(ChatLink.Task("t1"), parseChatLink("inventoria://timer/t1"))
        assertEquals(ChatLink.Block("b1"), parseChatLink("inventoria://block/b1"))
        assertEquals(ChatLink.Todo("x"), parseChatLink("INVENTORIA://todo/x?from=chat"))
    }

    @Test
    fun `anything that is not a usable app link is null`() {
        assertNull(parseChatLink("https://example.com"))
        assertNull(parseChatLink("inventoria://item/notanumber"))
        assertNull(parseChatLink("inventoria://todo/"))
        assertNull(parseChatLink("inventoria://unknown/1"))
        assertNull(parseChatLink("inventoria://"))
    }
}
