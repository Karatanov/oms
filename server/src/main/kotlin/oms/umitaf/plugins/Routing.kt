package oms.umitaf.plugins

import io.ktor.http.CacheControl
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import oms.umitaf.api.*
import java.io.File

/**
 * Реєструє всі HTTP-маршрути застосунку.
 *
 * A VPS deployment provides OMS_WEB_DIR so the browser application and API
 * share one origin. The redirect preserves the public-host fallback when that
 * directory is not configured.
 */
fun Application.configureRouting() {

    routing {

        // Службові маршрути системи.
        healthRoutes()

        // Маршрути роботи з ролями.
        roleRoutes()

        // Маршрути роботи з користувачами.
        userRoutes()

        // Маршрути автентифікації.
        authRoutes()

        // Маршрути роботи з проєктами.
        projectRoutes()

        mapRoutes()

        inspectionRoutes()

        financialRoutes()

        procurementRoutes()

        documentRoutes()
        photoRoutes()
        dashboardRoutes()

        val frontendDirectory = System.getenv("OMS_WEB_DIR")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::File)
        if (frontendDirectory?.isDirectory == true) {
            // Keep the initial page independent from the large Kotlin/JS
            // application bundle. The bundle is requested only after login.
            get("/") {
                call.respondFile(File(frontendDirectory, "login.html"))
            }
            staticFiles("/", frontendDirectory, index = "index.html") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
                cacheControl { file ->
                    val fingerprinted = Regex("""composeApp\.[0-9a-f]{12}\.js|[0-9a-f]{20}\.wasm""")
                        .matches(file.name)
                    // The Skiko runtime is versioned with the fingerprint of
                    // its owning JS bundle (as a query string in the bundle),
                    // so it is safe to retain it just like a hashed asset.
                    if (fingerprinted || file.name == "skiko.wasm") {
                        listOf(CacheControl.MaxAge(maxAgeSeconds = 31_536_000, visibility = CacheControl.Visibility.Public))
                    } else {
                        // HTML and non-fingerprinted assets must revalidate so a
                        // release never strands browsers on stale entry points.
                        listOf(CacheControl.NoCache(CacheControl.Visibility.Public))
                    }
                }
            }
        } else {
            // Production VPS Compose always sets OMS_WEB_DIR. Keep a safe
            // public-host fallback for incomplete server configuration.
            get("/") {
                call.respondRedirect("https://ua-oms.com/", permanent = false)
            }
        }
    }
}
