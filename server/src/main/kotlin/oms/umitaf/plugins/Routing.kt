package oms.umitaf.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.response.respondRedirect
import oms.umitaf.api.*
import java.io.File

/**
 * Реєструє всі HTTP-маршрути застосунку.
 *
 * A VPS deployment provides OMS_WEB_DIR so the browser application and API
 * share one origin.  The redirect preserves the legacy Render fallback when
 * that directory is not configured.
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
        staticFiles("/", frontendDirectory, index = "index.html") {
            preCompressed(CompressedFileType.GZIP)
        }
        } else {
            // Keep old Render bookmarks useful until that temporary fallback
            // is retired. Production VPS Compose always sets OMS_WEB_DIR.
            get("/") {
                call.respondRedirect("https://karatanov.github.io/oms/", permanent = false)
            }
        }
    }
}
