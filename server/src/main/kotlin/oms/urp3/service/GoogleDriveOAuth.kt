package oms.urp3.service

import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Short-lived, single-use authorizations. No refresh tokens or Google passwords are stored. */
internal object GoogleDriveOAuth {
    const val scope = "https://www.googleapis.com/auth/drive.readonly"
    private const val lifetime = 600_000L
    private val random = SecureRandom()
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build() }
    private data class Pending(val user: Long, val browser: String, val verifier: String, val expires: Long)
    private data class Grant(val user: Long, val browser: String, val token: String, val expires: Long)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val grants = ConcurrentHashMap<String, Grant>()
    private val cleanupExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "google-oauth-expiry").apply { isDaemon = true }
    }.apply { scheduleAtFixedRate({ clean() }, 1, 1, java.util.concurrent.TimeUnit.MINUTES) }
    private class Config(val id: String, val secret: String, val callback: String)
    val origin: String get() = URI(config().callback).let { "${it.scheme}://${it.rawAuthority}" }

    private fun config(): Config {
        val file = System.getenv("OMS_GOOGLE_OAUTH_CLIENT_FILE")?.takeIf(String::isNotBlank)
        val web = file?.let { Json.parseToJsonElement(File(it).readText()).jsonObject["web"]?.jsonObject }
        val id = web?.get("client_id")?.jsonPrimitive?.content ?: System.getenv("OMS_GOOGLE_CLIENT_ID")
        val secret = web?.get("client_secret")?.jsonPrimitive?.content ?: System.getenv("OMS_GOOGLE_CLIENT_SECRET")
        require(!id.isNullOrBlank() && !secret.isNullOrBlank()) { "Google OAuth is not configured on the OMS server. Use manual XLSX upload." }
        val callback = System.getenv("OMS_GOOGLE_REDIRECT_URI") ?: "https://ua-oms.com/api/v1/auth/google/callback"
        val uri = URI(callback)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null && uri.query == null) { "Invalid Google OAuth callback configuration." }
        return Config(id, secret, callback)
    }

    fun nonce(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun clean() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { it.value.expires <= now }
        grants.entries.removeIf { it.value.expires <= now }
    }
    private fun form(values: Map<String, String>) = values.entries.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, Charsets.UTF_8)}=${URLEncoder.encode(v, Charsets.UTF_8)}"
    }

    fun begin(user: Long, browser: String): String = config().let { begin(user, browser, it.id, it.callback) }

    internal fun begin(user: Long, browser: String, clientId: String, callback: String): String {
        clean()
        // Bound memory and replace abandoned attempts from the same browser.
        pending.entries.removeIf { it.value.user == user && it.value.browser == browser }
        require(pending.size < 1000) { "Too many Google connection attempts. Try again later." }
        val state = nonce()
        val verifier = nonce()
        pending[state] = Pending(user, browser, verifier, System.currentTimeMillis() + lifetime)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        return "https://accounts.google.com/o/oauth2/v2/auth?" + form(mapOf(
            "client_id" to clientId, "redirect_uri" to callback, "response_type" to "code",
            "scope" to scope, "access_type" to "online", "state" to state,
            "code_challenge" to challenge, "code_challenge_method" to "S256", "prompt" to "select_account"
        ))
    }

    fun complete(user: Long, browser: String, state: String, code: String?, denied: Boolean): String {
        clean()
        val attempt = pending[state]
        require(attempt != null && attempt.user == user && attempt.browser == browser) { "Google authorization expired or belongs to another browser. Try again." }
        require(pending.remove(state, attempt)) { "Google authorization already used." }
        require(!denied && !code.isNullOrBlank()) { "Google authorization was cancelled." }
        val config = config()
        val request = HttpRequest.newBuilder(URI("https://oauth2.googleapis.com/token"))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(mapOf(
                "client_id" to config.id, "client_secret" to config.secret, "redirect_uri" to config.callback,
                "grant_type" to "authorization_code", "code" to code, "code_verifier" to attempt.verifier
            )))).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) { "Google could not complete authorization. Check OAuth settings and try again." }
        val data = Json.parseToJsonElement(response.body()).jsonObject
        require(data["scope"]?.jsonPrimitive?.content?.split(' ')?.contains(scope) == true) { "Google Drive read permission was not granted." }
        val token = data["access_token"]?.jsonPrimitive?.content
        require(!token.isNullOrBlank()) { "Google did not return an access token." }
        val expires = data["expires_in"]?.jsonPrimitive?.longOrNull ?: 0
        require(expires > 60) { "Google authorization has expired. Try again." }
        grants.entries.removeIf { it.value.user == user && it.value.browser == browser }
        require(grants.size < 1000) { "Too many pending Google imports. Try again later." }
        return nonce().also { grants[it] = Grant(user, browser, token, System.currentTimeMillis() + minOf(lifetime, (expires - 30) * 1000)) }
    }

    fun consume(user: Long, browser: String, ticket: String): String {
        clean()
        val grant = grants[ticket]
        require(grant != null && grant.user == user && grant.browser == browser && grants.remove(ticket, grant)) { "Connect Google again before updating the registry." }
        return grant.token
    }
}
