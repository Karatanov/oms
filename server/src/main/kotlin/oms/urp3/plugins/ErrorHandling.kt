package oms.urp3.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.*
import oms.urp3.dto.ErrorResponse

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

            // Keep diagnostic context on the server.  JDBC, file-system and
            // provider exceptions can otherwise disclose implementation
            // details to an authenticated browser response.
            call.application.log.error(
                "Unhandled request failure for ${call.request.httpMethod.value} ${call.request.path()}",
                cause
            )

            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(
                    error = "INTERNAL_SERVER_ERROR",
                    message = "An unexpected server error occurred. Please try again later."
                )
            )
        }
    }
}
