package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.ErrorResponse
import oms.ufsi.dto.LoginRequest
import oms.ufsi.dto.LoginResponse
import oms.ufsi.dto.toResponse

/**
 * Маршрути автентифікації.
 */
fun Route.authRoutes() {

    val authService = AppContainer.authService

    /**
     * Вхід користувача до системи.
     */
    post("/api/v1/auth/login") {

        val request =
            call.receive<LoginRequest>()

        try {

            val user = authService.login(
                username = request.username,
                password = request.password
            )

            call.respond(
                LoginResponse(
                    user = user.toResponse()
                )
            )

        } catch (exception: IllegalArgumentException) {

            call.respond(
                HttpStatusCode.Unauthorized,

                ErrorResponse(
                    error = "AUTHENTICATION_ERROR",
                    message = exception.message
                        ?: "Помилка автентифікації."
                )
            )
        }
    }
}