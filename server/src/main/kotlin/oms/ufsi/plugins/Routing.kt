package oms.ufsi.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.respondRedirect
import oms.ufsi.api.*

/**
 * Реєструє всі HTTP-маршрути застосунку.
 *
 * The production browser application is hosted by GitHub Pages. Render runs
 * the API and file services only; its root redirects users to that UI.
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

        // Keep old Render bookmarks useful after the UI moved to GitHub Pages.
        get("/") {
            call.respondRedirect("https://karatanov.github.io/oms/", permanent = false)
        }
    }
}
