package oms.umitaf.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders

/**
 * Adds browser protections that are safe for the SPA and external map tiles.
 *
 * Content-Security-Policy and HSTS intentionally remain at the reverse-proxy
 * layer: both need deployment-specific allow-lists and TLS knowledge.
 */
fun Application.configureSecurityHeaders() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        header("X-Frame-Options", "SAMEORIGIN")
    }
}
