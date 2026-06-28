package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.dto.HealthResponse

/**
 * Реєструє службові маршрути застосунку.
 *
 * Такі маршрути не пов'язані з бізнес-логікою системи,
 * а використовуються для перевірки працездатності сервера.
 */
fun Routing.healthRoutes() {

    /**
     * Головна сторінка сервера.
     *
     * Поки що використовується лише для швидкої перевірки,
     * що backend успішно запущений.
     */
    get("/") {
        call.respondText("OMS backend is running")
    }

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
    get("/api/v1/health") {

        call.respond(
            HealthResponse(
                status = "UP"
            )
        )
    }
}