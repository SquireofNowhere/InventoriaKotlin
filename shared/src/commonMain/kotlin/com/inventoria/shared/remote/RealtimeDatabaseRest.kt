package com.inventoria.shared.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

class DatabaseException(val status: Int, message: String) : Exception(message)

/**
 * The Realtime Database's REST API: GET a node as JSON, PATCH children into it. Every call goes
 * out with a current ID token from [idToken], so the database rules see the same auth.uid the
 * Android SDK would present.
 */
class RealtimeDatabaseRest(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val idToken: suspend (forceRefresh: Boolean) -> String
) {
    private val baseUrl = config.databaseUrl.trimEnd('/')

    suspend fun get(path: String): JsonElement {
        val text = authorized { token -> http.get("$baseUrl/$path.json") { parameter("auth", token) } }
        return InventoriaJson.parseToJsonElement(text)
    }

    suspend fun patch(path: String, children: JsonObject) {
        authorized { token ->
            http.patch("$baseUrl/$path.json") {
                parameter("auth", token)
                contentType(ContentType.Application.Json)
                setBody(children.toString())
            }
        }
    }

    /**
     * Every child of the node at [path] decoded as [T]. A child that cannot be decoded is skipped
     * rather than failing the whole node -- one malformed row should not blank a screen.
     */
    suspend fun <T> getChildren(path: String, serializer: KSerializer<T>): List<T> =
        childrenOf(get(path)).mapNotNull { element ->
            runCatching { InventoriaJson.decodeFromJsonElement(serializer, element) }.getOrNull()
        }

    /** Retries once with a forced token refresh on 401, the answer to an expired token. */
    private suspend fun authorized(call: suspend (token: String) -> HttpResponse): String {
        var response = call(idToken(false))
        if (response.status == HttpStatusCode.Unauthorized) response = call(idToken(true))
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw DatabaseException(response.status.value, "Database ${response.status.value}: ${body.take(300)}")
        }
        return body
    }

    companion object {
        /**
         * A node's children. The database hands back a node whose keys all look like 0..n as a
         * JSON array (with nulls in the gaps) instead of an object, so both shapes are accepted.
         */
        fun childrenOf(node: JsonElement): List<JsonElement> = when (node) {
            is JsonObject -> node.values.toList()
            is JsonArray -> node.filter { it !is JsonNull }
            else -> emptyList()
        }
    }
}
