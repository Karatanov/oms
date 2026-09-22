package oms.umitaf.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.umitaf.dto.HealthResponse

/**
 * Реєструє службові маршрути застосунку.
 *
 * Такі маршрути не пов'язані з бізнес-логікою системи,
 * а використовуються для перевірки працездатності сервера.
 */
fun Routing.healthRoutes() {

    /**
     * Endpoint для перевірки стану системи.
     *
     * У майбутньому тут з'являться перевірки:
     * - доступності бази даних;
     * - файлового сховища MinIO;
     * - зовнішніх сервісів.
     */
    /**
     * Endpoint перевірки працездатності API.
     */
    get("/health") {

        call.respond(
            HealthResponse(
                status = "UP"
            )
        )
    }

    // Keep the existing API health-check URL for local tools and API clients.
    get("/api/v1/health") {

        call.respond(
            HealthResponse(
                status = "UP"
            )
        )
    }
}
