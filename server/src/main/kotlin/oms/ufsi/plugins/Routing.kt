package oms.ufsi.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import oms.ufsi.api.*

/**
 * Реєструє всі HTTP-маршрути застосунку.
 *
 * У майбутньому тут будуть підключатися:
 * - authRoutes();
 * - userRoutes();
 * - projectRoutes();
 * - inspectionRoutes();
 * - financialRoutes();
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
    }
}
