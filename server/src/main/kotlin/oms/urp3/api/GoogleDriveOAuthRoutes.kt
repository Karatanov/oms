package oms.urp3.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import oms.urp3.dto.ErrorResponse
import oms.urp3.service.GoogleDriveOAuth

internal const val googleBrowserCookie = "oms_google_browser"
@Serializable private data class GoogleStartResponse(val url: String)

fun Route.googleDriveOAuthRoutes() {
    post("/api/v1/auth/google/start") {
        val user = call.requireRole("ADMIN") ?: return@post
        call.response.header(HttpHeaders.CacheControl, "no-store")
        try {
            require(call.request.headers[HttpHeaders.Origin] == GoogleDriveOAuth.origin) { "Google connection must start on the OMS website." }
            val browser = call.request.cookies[googleBrowserCookie]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) } ?: GoogleDriveOAuth.nonce()
            val url = GoogleDriveOAuth.begin(user.userId, browser)
            call.response.cookies.append(Cookie(googleBrowserCookie, browser, path = "/api/v1", secure = true, httpOnly = true,
                extensions = mapOf("SameSite" to "Lax")))
            call.respond(GoogleStartResponse(url))
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("GOOGLE_CONNECTION_FAILED", "Google connection is unavailable. Check server OAuth configuration or upload XLSX manually."))
        }
    }
    get("/api/v1/auth/google/callback") {
        val user = call.requireRole("ADMIN") ?: return@get
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header("Referrer-Policy", "no-referrer")
        val scriptNonce = GoogleDriveOAuth.nonce()
        call.response.header("Content-Security-Policy", "default-src 'none'; script-src 'nonce-$scriptNonce'; frame-ancestors 'none'; base-uri 'none'")
        var ticket = ""
        var error = ""
        try {
            ticket = withContext(Dispatchers.IO) {
                GoogleDriveOAuth.complete(user.userId, call.request.cookies[googleBrowserCookie].orEmpty(),
                    call.request.queryParameters["state"].orEmpty(), call.request.queryParameters["code"],
                    call.request.queryParameters["error"] != null)
            }
        } catch (_: Exception) {
            error = "Google connection was cancelled, expired or failed. Close this window and try again, or upload XLSX manually."
        }
        val target = runCatching { GoogleDriveOAuth.origin }.getOrDefault("https://ua-oms.com")
        call.respondText("""<!doctype html><html><head><meta charset="utf-8"><title>OMS — Google Drive</title></head>
            <body><p>${if (ticket.isNotEmpty()) "Google connected. Return to OMS." else error}</p>
            <script nonce="$scriptNonce">history.replaceState(null,'',location.pathname);
            if(window.opener){window.opener.postMessage({type:'oms-google-drive',ticket:${JsonPrimitive(ticket)},error:${JsonPrimitive(error)}},${JsonPrimitive(target)});window.close();}</script></body></html>""", ContentType.Text.Html)
    }
}
