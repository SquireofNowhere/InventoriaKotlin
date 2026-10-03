package com.inventoria.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.PrintStream

/** One callable operation. [handler] returns the JSON the model sees; it signals trouble with [ToolError]. */
class Tool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val readOnly: Boolean,
    val destructive: Boolean = false,
    val handler: suspend (Args) -> JsonElement
)

/**
 * A minimal Model Context Protocol server over stdio: newline-delimited JSON-RPC 2.0, handling the
 * handshake, `ping`, `tools/list` and `tools/call`. Written against the spec directly rather than
 * an SDK because that is all a tools-only server needs, and it keeps the module's dependencies to
 * what :shared already brings.
 *
 * stdout carries protocol messages and nothing else; every log line goes to stderr.
 */
class McpServer(
    private val tools: List<Tool>,
    private val serverName: String,
    private val serverVersion: String,
    private val instructions: String
) {
    private val byName = tools.associateBy { it.name }
    private val compact = Json { encodeDefaults = true }

    suspend fun serve(input: BufferedReader, output: PrintStream) {
        while (true) {
            val line = withContext(Dispatchers.IO) { input.readLine() } ?: return
            if (line.isBlank()) continue
            val message = try {
                Json.parseToJsonElement(line)
            } catch (e: Exception) {
                send(output, errorResponse(JsonNull, -32700, "Parse error"))
                continue
            }
            // Batching was dropped from the protocol, but accepting it costs nothing.
            val messages = if (message is JsonArray) message.toList() else listOf(message)
            for (single in messages) {
                val response = try {
                    handle(single)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Unhandled error: $e")
                    errorResponse((single as? JsonObject)?.get("id") ?: JsonNull, -32603, "Internal error: ${e.message}")
                }
                if (response != null) send(output, response)
            }
        }
    }

    private suspend fun handle(message: JsonElement): JsonObject? {
        val request = message as? JsonObject ?: return errorResponse(JsonNull, -32600, "Invalid request")
        val id = request["id"]
        val method = (request["method"] as? JsonPrimitive)?.contentOrNull
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        // A message with no id is a notification: never answered, whatever it says.
        if (method == null) return null
        if (id == null) return null

        return when (method) {
            "initialize" -> result(id, initializeResult(params))
            "ping" -> result(id, JsonObject(emptyMap()))
            "tools/list" -> result(id, buildJsonObject { put("tools", JsonArray(tools.map { describe(it) })) })
            "tools/call" -> callTool(id, params)
            else -> errorResponse(id, -32601, "Method not found: $method")
        }
    }

    private fun initializeResult(params: JsonObject): JsonObject {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        val version = if (requested in SUPPORTED_VERSIONS) requested!! else SUPPORTED_VERSIONS.first()
        return buildJsonObject {
            put("protocolVersion", version)
            put("capabilities", obj("tools" to obj("listChanged" to false)))
            put("serverInfo", obj("name" to serverName, "version" to serverVersion))
            put("instructions", instructions)
        }
    }

    private fun describe(tool: Tool): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("inputSchema", tool.inputSchema)
        put(
            "annotations",
            obj(
                "readOnlyHint" to tool.readOnly,
                "destructiveHint" to tool.destructive,
                "openWorldHint" to false
            )
        )
    }

    private suspend fun callTool(id: JsonElement, params: JsonObject): JsonObject {
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull
        val tool = byName[name] ?: return errorResponse(id, -32602, "Unknown tool: $name")
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            val output = tool.handler(Args(arguments))
            result(id, toolResult(compact.encodeToString(JsonElement.serializer(), output), isError = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolError) {
            result(id, toolResult(e.message ?: "Tool error", isError = true))
        } catch (e: Exception) {
            // Network and database failures: report them to the model so it can tell the user,
            // rather than as a protocol error the client may swallow.
            log("Tool ${tool.name} failed: $e")
            result(id, toolResult("${tool.name} failed: ${e.message ?: e::class.simpleName}", isError = true))
        }
    }

    private fun toolResult(text: String, isError: Boolean): JsonObject {
        val clipped = if (text.length > MAX_RESULT_CHARS) {
            text.take(MAX_RESULT_CHARS) + "\n[output truncated at $MAX_RESULT_CHARS characters; narrow the query or lower 'limit']"
        } else text
        return buildJsonObject {
            put("content", JsonArray(listOf(obj("type" to "text", "text" to clipped))))
            put("isError", isError)
        }
    }

    private fun result(id: JsonElement, result: JsonElement): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }

    private fun errorResponse(id: JsonElement, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("error", obj("code" to code, "message" to message))
        }

    private fun send(output: PrintStream, message: JsonObject) {
        // '\n' rather than println: on Windows println would end the line with \r\n.
        output.print(compact.encodeToString(JsonElement.serializer(), message))
        output.print('\n')
        output.flush()
    }

    private companion object {
        /** Newest first; an unknown client version is answered with the first. */
        val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
        const val MAX_RESULT_CHARS = 120_000
    }
}

fun log(message: String) {
    System.err.println("[inventoria-mcp] $message")
}
