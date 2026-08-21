package oms.data

import kotlinx.browser.window

/**
 * Development uses the standalone Ktor server.  The Render image serves the
 * web client and API from the same HTTPS origin, so no deploy-time URL or CORS
 * configuration is needed for the demo.
 */
val omsApiBaseUrl: String = when (window.location.hostname) {
    "localhost", "127.0.0.1" -> "http://localhost:8080/api/v1"
    else -> "/api/v1"
}

/**
 * Converts an API-relative path into a URL suitable for the current runtime.
 * DTOs returned by the server already contain paths beginning with `/api/v1`,
 * whereas callers in the UI normally pass a path relative to that prefix.
 */
fun omsApiUrl(path: String): String = when {
    path.startsWith("http://") || path.startsWith("https://") -> path
    path.startsWith("/api/v1/") && omsApiBaseUrl.startsWith("http") ->
        "${omsApiBaseUrl.removeSuffix("/api/v1")}$path"
    path.startsWith("/api/v1/") -> path
    else -> "$omsApiBaseUrl$path"
}
