package oms.ufsi.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import oms.ufsi.api.healthRoutes

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
    }
}