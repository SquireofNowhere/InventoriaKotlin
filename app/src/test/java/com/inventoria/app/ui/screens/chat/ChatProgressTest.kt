package com.inventoria.app.ui.screens.chat

import com.inventoria.app.data.ChatMessage
import com.inventoria.app.data.ChatRole
import com.inventoria.app.data.Connection
import com.inventoria.app.data.ListenerPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProgressTest {

    private val t0 = 1_000_000L
    private val online = Connection(connected = true, serverOffset = 0L)
    private val offline = Connection(connected = false, serverOffset = 0L)

    private fun msg(
        status: String? = ChatMessage.STATUS_PENDING,
        sending: Boolean = false,
        activity: String? = null,
        activityText: String? = null
    ) = ChatMessage("k", ChatRole.USER, "hi", t0, status, activity, activityText, sending)

    private fun beat(secondsAgo: Long, busy: Boolean = false, now: Long) = ListenerPresence(now - secondsAgo * 1000, busy)

    private fun progress(m: ChatMessage?, presence: ListenerPresence?, conn: Connection = online, secondsLater: Long) =
        chatProgress(m, presence, conn, t0 + secondsLater * 1000)

    @Test
    fun `nothing is shown when nothing is awaited`() {
        assertNull(progress(null, ListenerPresence.NEVER, secondsLater = 5))
    }

    @Test
    fun `a message still on the phone says sending, then that the connection is slow`() {
        assertEquals("Sending…", progress(msg(sending = true), null, secondsLater = 2)!!.headline)
        assertNotNull(progress(msg(sending = true), null, secondsLater = 20)!!.detail)
    }

    @Test
    fun `sending while the phone has no connection says it is waiting, as a warning`() {
        val p = progress(msg(sending = true), null, offline, secondsLater = 3)!!
        assertEquals("Waiting for a connection", p.headline)
        assertEquals(ProgressTone.WARNING, p.tone)
        assertFalse(p.busy)
    }

    @Test
    fun `a sent message with a live listener waits patiently`() {
        val now = t0 + 5_000
        val p = progress(msg(), beat(10, now = now), secondsLater = 5)!!
        assertEquals("Sent. Waiting for Claude…", p.headline)
        assertEquals(ProgressTone.INFO, p.tone)
        assertTrue(p.busy)
    }

    @Test
    fun `a busy listener says it is finishing another request first`() {
        val now = t0 + 5_000
        assertEquals(
            "Claude is finishing another request first.",
            progress(msg(), beat(5, busy = true, now = now), secondsLater = 5)!!.detail
        )
    }

    @Test
    fun `no listener ever seen is checked first, then waited on, then doubted`() {
        assertEquals("Sent. Checking that Claude's PC is online…", progress(msg(), ListenerPresence.NEVER, secondsLater = 5)!!.headline)
        assertEquals("Sent. Waiting for Claude…", progress(msg(), ListenerPresence.NEVER, secondsLater = 30)!!.headline)
        val doubtful = progress(msg(), ListenerPresence.NEVER, secondsLater = 90)!!
        assertEquals("Nothing has picked this up yet", doubtful.headline)
        assertEquals(ProgressTone.WARNING, doubtful.tone)
        assertFalse(doubtful.busy)
    }

    @Test
    fun `a listener that was seen and has gone is called out sooner`() {
        val now = t0 + 40_000
        val p = progress(msg(), beat(200, now = now), secondsLater = 40)!!
        assertEquals("Claude's PC isn't connected", p.headline)
        assertEquals(ProgressTone.WARNING, p.tone)
    }

    @Test
    fun `an unread heartbeat is not mistaken for a missing listener`() {
        assertEquals("Sent. Checking that Claude's PC is online…", progress(msg(), null, secondsLater = 2)!!.headline)
    }

    @Test
    fun `working shows the phase the listener reports`() {
        val now = t0 + 4_000
        val live = beat(2, busy = true, now = now)
        fun headline(m: ChatMessage) = progress(m, live, secondsLater = 4)!!.headline
        assertEquals("Claude is thinking…", headline(msg("working", activity = "thinking")))
        assertEquals("Looking up todos…", headline(msg("working", activity = "looking", activityText = "Looking up todos")))
        assertEquals("Updating todo…", headline(msg("working", activity = "changing", activityText = "Updating todo")))
        assertEquals("Claude is writing the reply…", headline(msg("working", activity = "writing")))
        assertEquals("Claude is working on it…", headline(msg("working")))
    }

    @Test
    fun `working shows how long it has been, and eases the worry when it is long`() {
        val live = { s: Long -> beat(2, busy = true, now = t0 + s * 1000) }
        assertNull(progress(msg("working"), live(5), secondsLater = 5)!!.detail)
        assertEquals("30s so far", progress(msg("working"), live(30), secondsLater = 30)!!.detail)
        assertTrue(progress(msg("working"), live(125), secondsLater = 125)!!.detail!!.startsWith("2m 05s so far"))
    }

    @Test
    fun `working with a listener that stopped checking in is a warning, not an endless spinner`() {
        val now = t0 + 120_000
        val p = progress(msg("working", activity = "thinking"), beat(100, now = now), secondsLater = 120)!!
        assertEquals("Lost contact with Claude's PC", p.headline)
        assertEquals(ProgressTone.WARNING, p.tone)
        assertFalse(p.busy)
    }

    @Test
    fun `working with an old listener that never beats keeps its spinner`() {
        val p = progress(msg("working"), ListenerPresence.NEVER, secondsLater = 200)!!
        assertEquals(ProgressTone.INFO, p.tone)
        assertTrue(p.busy)
    }

    @Test
    fun `an offline phone with a sent message says so`() {
        val p = progress(msg(), beat(5, now = t0 + 5_000), offline, secondsLater = 5)!!
        assertEquals("This phone is offline", p.headline)
        assertEquals(ProgressTone.WARNING, p.tone)
    }

    @Test
    fun `server clock offset is applied when judging the heartbeat`() {
        // The phone's clock runs a minute behind the server's: a beat that is 10 s old on the
        // server's clock looks 70 s old on the phone's unless the offset is added.
        val serverNow = t0 + 5_000 + 60_000
        val skewed = Connection(connected = true, serverOffset = 60_000L)
        val presence = ListenerPresence(serverNow - 10_000, busy = false)
        assertEquals(LinkState.ONLINE, linkState(presence, skewed, t0 + 5_000))
        assertEquals(LinkState.PC_OFFLINE, linkState(presence, online, t0 + 5_000 + 200_000))
    }

    @Test
    fun `the link line covers each state`() {
        val now = t0
        assertEquals(LinkState.PHONE_OFFLINE, linkState(beat(1, now = now), offline, now))
        assertEquals(LinkState.CHECKING, linkState(null, online, now))
        assertEquals(LinkState.BUSY, linkState(beat(1, busy = true, now = now), online, now))
        assertEquals(LinkState.PC_NOT_SEEN, linkState(ListenerPresence.NEVER, online, now))
        assertTrue(LinkState.ONLINE.isHealthy)
        assertFalse(LinkState.PC_OFFLINE.isHealthy)
    }

    @Test
    fun `each message gets a caption for where it has got to`() {
        fun label(status: String?, sending: Boolean = false) = deliveryLabel(msg(status, sending))
        assertEquals("Sending…", label("pending", sending = true))
        assertEquals("Sent", label("pending"))
        assertEquals("Claude is on it", label("working"))
        assertEquals("Not answered", label("error"))
        assertNull(label("done"))
        assertNull(deliveryLabel(ChatMessage("r", ChatRole.ASSISTANT, "hello", t0, null)))
    }

    @Test
    fun `elapsed time is written as seconds then minutes`() {
        assertEquals("0s", elapsed(900))
        assertEquals("59s", elapsed(59_000))
        assertEquals("1m 00s", elapsed(60_000))
        assertEquals("2m 05s", elapsed(125_000))
    }
}
