package oms.urp3.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

/**
 * Compresses sufficiently large API and HTML responses when the browser
 * advertises gzip support.  Modern browsers transparently decompress these
 * responses, so this reduces transfer time without changing API contracts.
 *
 * Fingerprinted Compose assets are already pre-compressed by the static-file
 * handler.  This plugin complements that behaviour for dynamic JSON payloads
 * such as project, procurement, and financial registries.
 */
fun Application.configureCompression() {
    install(Compression) {
        gzip {
            // Do not spend CPU compressing tiny responses such as health checks.
            minimumSize(1_024)
            priority = 1.0
        }
    }
}
