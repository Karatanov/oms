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
    // Runtime images intentionally do not contain .git. CI injects these
    // non-sensitive values at build time so an external deploy check can
    // prove which immutable image is serving production.
    fun response() = HealthResponse(
        status = "UP",
        gitSha = System.getenv("OMS_GIT_SHA") ?: "unknown",
        buildTime = System.getenv("OMS_BUILD_TIME") ?: "unknown"
    )

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

        call.respond(response())
    }

    // Keep the existing API health-check URL for local tools and API clients.
    get("/api/v1/health") {

        call.respond(response())
    }
}
