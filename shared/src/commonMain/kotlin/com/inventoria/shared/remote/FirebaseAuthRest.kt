package com.inventoria.shared.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.inventoria.shared.model.nowMillis

/** A signed-in Firebase user, as much of it as a REST client needs to act for them. */
@Serializable
data class AuthSession(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    /** When [idToken] stops being accepted. Refreshed a minute early. */
    val expiresAt: Long,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
) {
    fun needsRefresh(now: Long = nowMillis()): Boolean = now >= expiresAt - 60_000L
}

class FirebaseAuthException(message: String) : Exception(message)

/**
 * Firebase Authentication over its REST API (Identity Toolkit + Secure Token), which is what the
 * Firebase SDKs call underneath. Google is the only provider, same as the Android app.
 */
class FirebaseAuthRest(
    private val http: HttpClient,
    private val config: FirebaseConfig
) {
    /** Exchanges a Google OAuth access token (from Google Identity Services) for a Firebase session. */
    suspend fun signInWithGoogleAccessToken(accessToken: String): AuthSession =
        signInWithIdp("access_token=$accessToken&providerId=google.com")

    /** Same, for a Google ID token (from One Tap / the Sign in with Google button). */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthSession =
        signInWithIdp("id_token=$idToken&providerId=google.com")

    private suspend fun signInWithIdp(postBody: String): AuthSession {
        val response = http.post("$IDENTITY_TOOLKIT/accounts:signInWithIdp") {
            parameter("key", config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(SignInWithIdpRequest(postBody = postBody, requestUri = "http://localhost"))
        }
        val body: SignInWithIdpResponse = response.ensureOk().body()
        return AuthSession(
            uid = body.localId,
            idToken = body.idToken,
            refreshToken = body.refreshToken,
            expiresAt = nowMillis() + body.expiresIn.toLong() * 1000L,
            email = body.email,
            displayName = body.displayName,
            photoUrl = body.photoUrl
        )
    }

    /** A fresh ID token for [session]. Fails if the account was deleted or disabled. */
    suspend fun refresh(session: AuthSession): AuthSession {
        val response = http.submitForm(
            url = "$SECURE_TOKEN/token?key=${config.apiKey}",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", session.refreshToken)
            }
        )
        val body: RefreshResponse = response.ensureOk().body()
        return session.copy(
            uid = body.userId,
            idToken = body.idToken,
            refreshToken = body.refreshToken,
            expiresAt = nowMillis() + body.expiresIn.toLong() * 1000L
        )
    }

    private suspend fun HttpResponse.ensureOk(): HttpResponse {
        if (!status.isSuccess()) {
            throw FirebaseAuthException("Firebase Auth ${status.value}: ${bodyAsText().take(300)}")
        }
        return this
    }

    @Serializable
    private data class SignInWithIdpRequest(
        val postBody: String,
        val requestUri: String,
        val returnSecureToken: Boolean = true,
        val returnIdpCredential: Boolean = true
    )

    @Serializable
    private data class SignInWithIdpResponse(
        val localId: String,
        val idToken: String,
        val refreshToken: String,
        val expiresIn: String,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null
    )

    @Serializable
    private data class RefreshResponse(
        @SerialName("user_id") val userId: String,
        @SerialName("id_token") val idToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: String
    )

    private companion object {
        const val IDENTITY_TOOLKIT = "https://identitytoolkit.googleapis.com/v1"
        const val SECURE_TOKEN = "https://securetoken.googleapis.com/v1"
    }
}
