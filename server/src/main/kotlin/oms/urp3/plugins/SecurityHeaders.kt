package oms.urp3.plugins

import io.ktor.http.append
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call

/**
 * Adds browser protections that are safe for the SPA and external map tiles.
 *
 * Content-Security-Policy and HSTS intentionally remain at the reverse-proxy
 * layer: both need deployment-specific allow-lists and TLS knowledge.
 */
fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Setup) {
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Referrer-Policy", "strict-origin-when-cross-origin")
        call.response.headers.append("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        call.response.headers.append("X-Frame-Options", "SAMEORIGIN")
    }
}
