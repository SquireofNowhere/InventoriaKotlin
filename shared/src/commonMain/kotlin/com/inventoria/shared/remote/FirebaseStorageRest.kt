package com.inventoria.shared.remote

import com.inventoria.shared.model.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random

class StorageException(message: String) : Exception(message)

/**
 * Firebase Storage over its REST API, for uploading an item photo the way the phone does
 * (FirebaseStorageRepository.uploadItemImage): into the uploader's *own* `item_images` folder, which
 * is the only place storage.rules lets anyone write, and handing back the tokenized download URL.
 * That URL is what the item row stores, and what every other device loads the picture from.
 */
class FirebaseStorageRest(
    private val http: HttpClient,
    private val bucket: String,
    private val idToken: suspend (forceRefresh: Boolean) -> String
) {
    /** Uploads [bytes] as an image and returns its download URL. [extension] is only the file's suffix. */
    suspend fun uploadItemImage(uid: String, bytes: ByteArray, mimeType: String, extension: String): String {
        val name = "img_${nowMillis()}_${randomSuffix()}.$extension"
        // The object name is one URL segment, so its slashes must be escaped. Every part is
        // letters, digits, '_', '-' or '.', so nothing else needs it.
        val encoded = "users/$uid/item_images/$name".replace("/", "%2F")
        val objectUrl = "$BASE/b/$bucket/o/$encoded"

        suspend fun send(token: String): HttpResponse = http.post(objectUrl) {
            header("Authorization", "Firebase $token")
            contentType(ContentType.parse(mimeType))
            setBody(bytes)
        }

        var response = send(idToken(false))
        if (response.status == HttpStatusCode.Unauthorized) response = send(idToken(true))
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw StorageException("Storage ${response.status.value}: ${body.take(300)}")
        }
        val token = ((InventoriaJson.parseToJsonElement(body) as? JsonObject)?.get("downloadTokens") as? JsonPrimitive)
            ?.contentOrNull?.substringBefore(',')
            ?: throw StorageException("Storage accepted the upload but returned no download token")
        return "$objectUrl?alt=media&token=$token"
    }

    private fun randomSuffix(): String =
        buildString { repeat(12) { append("0123456789abcdef"[Random.nextInt(16)]) } }

    private companion object {
        const val BASE = "https://firebasestorage.googleapis.com/v0"
    }
}
