package com.dev.svn.psbdx.backup

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.dev.svn.psbdx.BuildConfig
import com.dev.svn.psbdx.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth 2.0 authorization-code flow with PKCE (RFC 7636) through the system browser / Custom Tabs.
 * No Google Play Services, no closed-source SDK. Scope: Drive "appDataFolder" only.
 *
 * Android-type OAuth clients have no client secret, so token requests are sent WITHOUT one first.
 * Only if Google answers invalid_client and a secret was injected at build time (Web-type client)
 * is the request repeated with the secret.
 */
class GoogleAuth(
    @Suppress("unused") private val context: Context,
    private val store: SecureStore,
    private val http: OkHttpClient,
) {
    /** False when the build has no GOOGLE_CLIENT_ID: the UI then hides / disables Drive sync. */
    val isConfigured: Boolean get() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()
    val isSignedIn: Boolean get() = isConfigured && store.refreshToken != null

    fun startSignIn(activity: Activity) {
        if (!isConfigured) return
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(16)
        store.pendingVerifier = verifier
        store.pendingState = state
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            B64_FLAGS,
        )
        val uri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.GOOGLE_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
        CustomTabsIntent.Builder().build().launchUrl(activity, uri)
    }

    /** Called with the com.dev.svn.psbdx:/oauth2redirect URI the browser hands back. */
    suspend fun handleRedirect(uri: Uri): Result<Unit> = runCatching {
        val error = uri.getQueryParameter("error")
        if (error != null) throw IOException("Google sign-in failed: $error")
        val expectedState = store.pendingState
        if (expectedState == null || uri.getQueryParameter("state") != expectedState) {
            throw IOException("Sign-in response did not match the request")
        }
        val code = uri.getQueryParameter("code") ?: throw IOException("No authorization code returned")
        val verifier = store.pendingVerifier ?: throw IOException("Missing PKCE verifier")
        saveTokens(
            tokenRequest { form ->
                form.add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", verifier)
            },
        )
        store.pendingState = null
        store.pendingVerifier = null
    }

    /** Returns a valid access token, refreshing it when needed. */
    suspend fun accessToken(): String {
        val cached = store.accessToken
        if (cached != null && System.currentTimeMillis() < store.accessExpiryMs - 60_000) return cached
        val refresh = store.refreshToken ?: throw IOException("Google Drive is not linked")
        try {
            saveTokens(
                tokenRequest { form ->
                    form.add("grant_type", "refresh_token").add("refresh_token", refresh)
                },
            )
        } catch (e: IOException) {
            if (e.message?.contains("invalid_grant") == true) clearTokens()
            throw e
        }
        return store.accessToken ?: throw IOException("No access token")
    }

    suspend fun signOut() {
        val token = store.refreshToken
        clearTokens()
        if (token != null) runCatching {
            post(FormBody.Builder().add("token", token).build(), REVOKE_ENDPOINT)
        }
    }

    private fun clearTokens() {
        store.refreshToken = null
        store.accessToken = null
        store.accessExpiryMs = 0
    }

    private fun saveTokens(json: JSONObject) {
        store.accessToken = json.getString("access_token")
        store.accessExpiryMs = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
        json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { store.refreshToken = it }
    }

    /** Sends a token-endpoint request: first without a secret (Android client), then with one if required. */
    private suspend fun tokenRequest(fields: (FormBody.Builder) -> FormBody.Builder): JSONObject {
        fun build(withSecret: Boolean): FormBody {
            val b = FormBody.Builder().add("client_id", BuildConfig.GOOGLE_CLIENT_ID)
            fields(b)
            if (withSecret) b.add("client_secret", BuildConfig.GOOGLE_CLIENT_SECRET)
            return b.build()
        }
        return try {
            post(build(false))
        } catch (e: IOException) {
            val canRetry = BuildConfig.GOOGLE_CLIENT_SECRET.isNotBlank() &&
                e.message?.contains("invalid_client") == true
            if (canRetry) post(build(true)) else throw e
        }
    }

    private suspend fun post(form: FormBody, url: String = TOKEN_ENDPOINT): JSONObject =
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).post(form).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val err = json?.optString("error").orEmpty()
                    val desc = json?.optString("error_description").orEmpty()
                    throw IOException("Google auth HTTP ${resp.code} $err $desc".trim())
                }
                if (body.isBlank()) JSONObject() else JSONObject(body)
            }
        }

    private fun randomUrlSafe(bytes: Int): String =
        Base64.encodeToString(ByteArray(bytes).also(SecureRandom()::nextBytes), B64_FLAGS)

    companion object {
        const val REDIRECT_URI = "com.dev.svn.psbdx:/oauth2redirect"
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val B64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    }
}
