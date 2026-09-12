package oms.ufsi.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.http.CacheControl
import oms.ufsi.api.*
import java.io.File

/**
 * Реєструє всі HTTP-маршрути застосунку.
 *
 * API routes are registered before the packaged Web static resources so that
 * the UI fallback can never shadow an API or health endpoint.
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

        // The Render image copies the Compose Web distribution to this folder.
        // Register this last so API and health routes always take precedence.
        staticFiles("/", File("static"), index = "index.html") {
            // The Compose bundle keeps a stable filename. Revalidate it on
            // every request so a browser cannot retain an older UI after a
            // Render deployment and show a different table layout.
            cacheControl { listOf(CacheControl.NoCache(null)) }
        }
    }
}
