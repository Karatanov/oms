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

fun omsApiUrl(path: String): String = "$omsApiBaseUrl$path"
