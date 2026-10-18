package oms.umitaf.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.umitaf.dto.HealthResponse
import oms.umitaf.dto.VersionResponse

/**
 * Реєструє службові маршрути застосунку.
 *
 * Такі маршрути не пов'язані з бізнес-логікою системи,
 * а використовуються для перевірки працездатності сервера.
 */
fun Routing.healthRoutes() {

    // This is deliberately independent from /health: a load balancer only
    // needs liveness, whereas the deployment workflow must verify the exact
    // immutable image revision that reached the public server.
    val revision = System.getenv("OMS_GIT_REVISION")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(12)
        ?: "local"

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

    get("/version") {
        call.respond(VersionResponse(revision = revision))
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
