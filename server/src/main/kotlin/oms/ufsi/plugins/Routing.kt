package oms.ufsi.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.respondResource
import io.ktor.server.routing.*
import oms.ufsi.api.*

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

        // The Render image packages the Compose Web distribution here.
        // Register this last so API and health routes always take precedence.
        // Explicitly handle the root path: Ktor's static route can otherwise
        // resolve it as an empty resource name after a container restart.
        get("/") {
            call.respondResource("static/index.html")
        }
        staticResources("/", "static", index = "index.html")
    }
}
