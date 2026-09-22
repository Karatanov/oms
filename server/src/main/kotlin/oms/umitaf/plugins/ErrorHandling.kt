package oms.umitaf.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import oms.umitaf.dto.ErrorResponse

/**
 * Налаштовує централізовану обробку помилок.
 *
 * Замість HTML-сторінок або внутрішніх повідомлень Ktor
 * клієнт завжди отримує JSON однакового формату.
 */
fun Application.configureErrorHandling() {

    install(StatusPages) {

        /**
         * Обробка всіх непередбачених винятків.
         */
        exception<Throwable> { call, cause ->

            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(
                    error = "INTERNAL_SERVER_ERROR",
                    message = cause.message ?: "Unknown error"
                )
            )
        }
    }
}