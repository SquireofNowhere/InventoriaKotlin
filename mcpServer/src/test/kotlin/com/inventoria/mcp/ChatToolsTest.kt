package com.inventoria.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatToolsTest {
    private fun json(text: String) = Json.parseToJsonElement(text)

    // --- keys ---

    @Test
    fun pushIdsAreTwentyCharactersAndSortByTime() {
        val random = Random(7)
        val early = newPushId(1_700_000_000_000L, random)
        val late = newPushId(1_700_000_000_001L, random)
        assertEquals(20, early.length)
        assertTrue(early < late, "$early should sort before $late")
        assertTrue(newPushId(1_700_000_000_000L, random) < newPushId(1_800_000_000_000L, random))
        assertNotEquals(newPushId(5L, Random(1)), newPushId(5L, Random(2)))
    }

    @Test
    fun aReplyKeySortsStraightAfterItsQuestionAndBeforeTheNextOne() {
        val random = Random(3)
        val question = newPushId(1_700_000_000_000L, random)
        val next = newPushId(1_700_000_000_001L, random)
        val reply = "$question~r"
        assertTrue(question < reply)
        assertTrue(reply < next)
    }

    @Test
    fun titlesComeFromTheFirstLineAndAreCutShort() {
        assertEquals("What is due today?", chatTitleFrom("  What is due today?\nand tomorrow"))
        assertEquals("New chat", chatTitleFrom("   \n  "))
        val long = chatTitleFrom("a".repeat(100))
        assertEquals(41, long.length)
        assertTrue(long.endsWith("…"))
    }

    // --- reading ---

    @Test
    fun messagesComeOldestFirstWithTheReplyAfterItsQuestion() {
        val node = json(
            """{"b2":{"role":"user","text":"second","createdAt":2000,"status":"pending"},
                "a1~r":{"role":"assistant","text":"first answer","createdAt":1500},
                "a1":{"role":"user","text":"first","createdAt":1000,"status":"done"},
                "_junk":{"at":5}}"""
        )
        val messages = chatMessages(node, 50)
        assertEquals(listOf("a1", "a1~r", "b2"), messages.map { it["id"]!!.jsonPrimitive.content })
        assertEquals(listOf("user", "assistant", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
    }

    @Test
    fun onlyTheLatestMessagesAreKept() {
        val node = json("""{"a":{"role":"user","text":"1"},"b":{"role":"user","text":"2"},"c":{"role":"user","text":"3"}}""")
        assertEquals(listOf("b", "c"), chatMessages(node, 2).map { it["id"]!!.jsonPrimitive.content })
        assertTrue(chatMessages(JsonNull, 5).isEmpty())
    }

    @Test
    fun entriesWithoutTextOrAKnownRoleAreNotMessages() {
        assertNull(chatMessageView("k", json("""{"role":"user"}""")))
        assertNull(chatMessageView("k", json("""{"text":"hi"}""")))
        assertNull(chatMessageView("k", json("""{"role":"system","text":"hi"}""")))
        assertNull(chatMessageView("k", JsonPrimitive(3)))
    }

    @Test
    fun aChatAwaitsAReplyWhileAUserMessageIsUnfinished() {
        fun awaits(status: String?): Boolean {
            val field = if (status == null) "" else ",\"status\":\"$status\""
            return awaitsReply(chatMessages(json("""{"a":{"role":"user","text":"hi"$field}}"""), 5))
        }
        assertTrue(awaits(null))
        assertTrue(awaits("pending"))
        assertTrue(awaits("working"))
        assertFalse(awaits("done"))
        assertFalse(awaits("error"))
        assertFalse(awaitsReply(chatMessages(json("""{"a":{"role":"assistant","text":"hi"}}"""), 5)))
    }

    @Test
    fun aChatSummaryFallsBackOnTheDefaultTitleAndCreationTime() {
        val summary = chatSummaryView("c1", json("""{"createdAt":5000,"state":"live","contextPercent":42}"""))!!
        assertEquals("New chat", summary["title"]!!.jsonPrimitive.content)
        assertEquals("5000", summary["updatedAt"]!!.jsonPrimitive.content)
        assertEquals("live", summary["sessionState"]!!.jsonPrimitive.content)
        assertNull(chatSummaryView("c2", JsonNull))
    }

    // --- model choice ---

    private val catalog = chatModels(
        json(
            """{"at":1,"models":[
                {"value":"default","name":"Default","efforts":["low","high"]},
                {"value":"haiku","name":"Haiku"}]}"""
        )
    )

    @Test
    fun theModelCatalogReadsArraysAndMissingEfforts() {
        assertEquals(listOf("default", "haiku"), catalog.map { it.value })
        assertEquals(listOf("low", "high"), catalog[0].efforts)
        assertTrue(catalog[1].efforts.isEmpty())
        assertTrue(chatModels(JsonNull).isEmpty())
        // the database hands a 0..n keyed object back as an array, but accept an object too
        assertEquals(1, chatModels(json("""{"models":{"0":{"value":"x"}}}""")).size)
    }

    @Test
    fun aChoiceTheCatalogDoesNotListIsRefused() {
        checkChatChoice(catalog, null, null)
        checkChatChoice(catalog, "haiku", null)
        checkChatChoice(catalog, null, "high")
        checkChatChoice(emptyList(), "anything", "whatever")
        assertFailsWith<ToolError> { checkChatChoice(catalog, "opus", null) }
        assertFailsWith<ToolError> { checkChatChoice(catalog, "haiku", "high") }
        assertFailsWith<ToolError> { checkChatChoice(catalog, null, "medium") }
    }

    // --- secrets ---

    @Test
    fun anAuthTokenInAnErrorIsBlanked() {
        val message = "Connect timeout has expired [url=https://db.firebaseio.com/users/u/items.json?auth=eyJabc.def-ghi_jkl, connect_timeout=unknown ms]"
        val clean = redactSecrets(message)
        assertFalse("eyJ" in clean, clean)
        assertTrue("?auth=REDACTED" in clean, clean)
        assertTrue("connect_timeout=unknown ms" in clean, clean)
        assertEquals("nothing to hide", redactSecrets("nothing to hide"))
        assertEquals("x?a=1&auth=REDACTED&b=2", redactSecrets("x?a=1&auth=tok&b=2"))
    }
}
