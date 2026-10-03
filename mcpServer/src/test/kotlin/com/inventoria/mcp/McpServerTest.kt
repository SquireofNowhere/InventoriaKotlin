package com.inventoria.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The protocol layer on its own: no network, no Firebase. */
class McpServerTest {
    private val echo = Tool(
        name = "echo",
        description = "Echoes 'text' back",
        inputSchema = schema("text" to strP("What to echo"), required = listOf("text")),
        readOnly = true
    ) { args -> obj("said" to args.reqStr("text")) }

    private val broken = Tool(
        name = "broken",
        description = "Always fails",
        inputSchema = schema(),
        readOnly = true
    ) { throw ToolError("nope") }

    /** Feeds [lines] to a fresh server and returns every JSON line it wrote, in order. */
    private fun converse(vararg lines: String): List<JsonObject> {
        val server = McpServer(listOf(echo, broken), "test", "0", "instructions")
        val bytes = ByteArrayOutputStream()
        runBlocking {
            server.serve(BufferedReader(StringReader(lines.joinToString("\n"))), PrintStream(bytes, true, Charsets.UTF_8))
        }
        return bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }.toList()
    }

    @Test
    fun initializeEchoesAKnownProtocolVersion() {
        val reply = converse("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}""").single()
        val result = reply["result"]!!.jsonObject
        assertEquals("2025-03-26", result["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("test", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun notificationsGetNoReply() {
        assertTrue(converse("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").isEmpty())
    }

    @Test
    fun toolsListDescribesEveryTool() {
        val reply = converse("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").single()
        val tools = reply["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(listOf("echo", "broken"), tools.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        val echoTool = tools.first().jsonObject
        assertEquals("object", echoTool["inputSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(echoTool["annotations"]!!.jsonObject["readOnlyHint"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun toolCallReturnsTextContent() {
        val reply = converse(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hi"}}}"""
        ).single()
        val result = reply["result"]!!.jsonObject
        assertEquals(false, result["isError"]!!.jsonPrimitive.boolean)
        val text = result["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("hi", Json.parseToJsonElement(text).jsonObject["said"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolErrorsComeBackAsIsErrorNotProtocolErrors() {
        val reply = converse("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"broken"}}""").single()
        assertNull(reply["error"])
        val result = reply["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.boolean)
        assertEquals("nope", result["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun missingRequiredArgumentIsAToolError() {
        val reply = converse("""{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"echo","arguments":{}}}""").single()
        assertEquals(true, reply["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun unknownToolAndMethodAreProtocolErrors() {
        val replies = converse(
            """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"missing"}}""",
            """{"jsonrpc":"2.0","id":7,"method":"resources/list"}"""
        )
        assertEquals(-32602, replies[0]["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(-32601, replies[1]["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun garbageGetsAParseError() {
        val reply = converse("not json").single()
        assertEquals(-32700, reply["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun argsDistinguishAbsentFromExplicitNull() {
        val args = Args(Json.parseToJsonElement("""{"a":null,"b":3,"c":"2.0"}""").jsonObject)
        assertTrue(args.has("a") && args.isNull("a"))
        assertTrue(!args.has("z") && !args.isNull("z"))
        assertEquals(3, args.int("b"))
        assertNull(args.str("a"))
        assertEquals(2.0, args.double("c"))
    }

    @Test
    fun datesAndTimesRoundTrip() {
        assertEquals("2026-10-03", dayString(parseDay("2026-10-03")))
        assertEquals(14 * 60 + 5, parseMinuteOfDay("14:05"))
        assertEquals("14:05", minuteString(14 * 60 + 5))
    }
}
