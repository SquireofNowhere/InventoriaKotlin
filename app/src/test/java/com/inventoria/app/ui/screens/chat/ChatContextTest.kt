package com.inventoria.app.ui.screens.chat

import com.inventoria.app.data.ChatRepository
import com.inventoria.app.data.ChatSummary
import com.inventoria.app.data.ContextLevel
import com.inventoria.app.data.contextLevelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextTest {

    private fun chat(
        state: String? = null,
        percent: Int = 0,
        tokens: Long = 0,
        window: Long = 0,
        lastActiveAt: Long = 0,
        idleMinutes: Int = 0
    ) = ChatSummary(
        id = "c", title = "t", createdAt = 1, updatedAt = 1, state = state, contextTokens = tokens,
        contextWindow = window, contextPercent = percent, lastActiveAt = lastActiveAt, idleMinutes = idleMinutes
    )

    @Test
    fun `token counts read the way a person says them`() {
        assertEquals("850", formatTokens(850))
        assertEquals("84k", formatTokens(84_300))
        assertEquals("200k", formatTokens(200_000))
        assertEquals("1M", formatTokens(1_000_000))
        assertEquals("1.2M", formatTokens(1_240_000))
    }

    @Test
    fun `context level turns at 60 and 85 percent`() {
        assertEquals(ContextLevel.FINE, contextLevelFor(0))
        assertEquals(ContextLevel.FINE, contextLevelFor(59))
        assertEquals(ContextLevel.GETTING_FULL, contextLevelFor(60))
        assertEquals(ContextLevel.GETTING_FULL, contextLevelFor(84))
        assertEquals(ContextLevel.FULL, contextLevelFor(85))
        assertEquals(ContextLevel.FULL, contextLevelFor(100))
    }

    @Test
    fun `advice appears only once a chat is filling up`() {
        assertNull(contextAdvice(ContextLevel.FINE))
        assertNotNull(contextAdvice(ContextLevel.GETTING_FULL))
        assertTrue(contextAdvice(ContextLevel.FULL)!!.contains("almost full"))
    }

    @Test
    fun `the context line says nothing until Claude has reported`() {
        assertEquals("Context empty", contextLine(null))
        assertEquals("Context empty", contextLine(chat()))
        assertEquals("Context 42% · 84k of 200k", contextLine(chat(percent = 42, tokens = 84_000, window = 200_000)))
        assertEquals("Context 42% · 84k", contextLine(chat(percent = 42, tokens = 84_000)))
    }

    @Test
    fun `a live session says when it closes, rounded up`() {
        val live = chat(state = "live", lastActiveAt = 1_000_000, idleMinutes = 60)
        assertEquals(60 * 60_000L - 600_000, live.closesInMillis(serverNow = 1_000_000 + 600_000))
        assertEquals("Session open · closes in 50 min if idle", sessionLine(live, serverNow = 1_000_000 + 600_000))
        assertEquals("Session open · closes in 1 min if idle", sessionLine(live, serverNow = 1_000_000 + 60 * 60_000L - 1_000))
        assertEquals("Session closing", sessionLine(live, serverNow = 1_000_000 + 61 * 60_000L))
        assertEquals("Session open · closes in 3 h if idle",
            sessionLine(chat(state = "live", lastActiveAt = 0 + 1, idleMinutes = 180), serverNow = 2))
    }

    @Test
    fun `a closed or never-started chat says what the next message does`() {
        assertTrue(sessionLine(null, 0).contains("first message"))
        assertTrue(sessionLine(chat(), 0).contains("first message"))
        val asleep = sessionLine(chat(state = "closed"), 0)
        assertTrue(asleep.contains("asleep"))
        assertTrue(asleep.contains("wakes it"))
        assertNull(chat(state = "closed").closesInMillis(0))
    }

    @Test
    fun `a chat is titled from the first line of its first message`() {
        assertEquals("What can I get done today?", ChatRepository.titleFrom("What can I get done today?"))
        assertEquals("First line", ChatRepository.titleFrom("  First line  \nsecond line"))
        assertEquals(ChatSummary.DEFAULT_TITLE, ChatRepository.titleFrom("   "))
        val long = ChatRepository.titleFrom("a".repeat(100))
        assertEquals(41, long.length)
        assertTrue(long.endsWith("…"))
    }
}
