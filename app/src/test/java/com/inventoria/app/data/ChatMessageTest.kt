package com.inventoria.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTest {

    private fun message(role: ChatRole, status: String?) =
        ChatMessage(id = "k", role = role, text = "hi", createdAt = 0L, status = status)

    @Test
    fun `a user message is awaiting a reply until the listener closes it`() {
        assertTrue(message(ChatRole.USER, "pending").isAwaitingReply)
        assertTrue(message(ChatRole.USER, "working").isAwaitingReply)
        assertTrue(message(ChatRole.USER, null).isAwaitingReply)
        assertFalse(message(ChatRole.USER, "done").isAwaitingReply)
        assertFalse(message(ChatRole.USER, "error").isAwaitingReply)
    }

    @Test
    fun `Claude's own messages are never awaiting a reply`() {
        assertFalse(message(ChatRole.ASSISTANT, null).isAwaitingReply)
        assertFalse(message(ChatRole.ASSISTANT, "pending").isAwaitingReply)
    }
}
