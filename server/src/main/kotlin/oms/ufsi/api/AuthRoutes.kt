package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.ErrorResponse
import oms.ufsi.dto.LoginRequest
import oms.ufsi.dto.ActivateAccountRequest
import oms.ufsi.dto.LoginResponse
import oms.ufsi.dto.toResponse
import oms.ufsi.security.UserSession
import oms.ufsi.security.JwtTokenService
import oms.ufsi.service.AccountLockedException

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

            call.sessions.set(UserSession(user.id, user.role.code))

            call.respond(
                LoginResponse(
                    user = user.toResponse(),
                    accessToken = JwtTokenService.issue(user.id, user.role.code)
                )
            )

        } catch (exception: AccountLockedException) {
            call.respond(
                HttpStatusCode.Locked,
                ErrorResponse("ACCOUNT_LOCKED", "Account is locked until ${exception.lockedUntil}.")
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

    post("/api/v1/auth/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.NoContent)
    }

    post("/api/v1/auth/activate") {
        try {
            val request = call.receive<ActivateAccountRequest>()
            val userId = AppContainer.activationService.activate(request.token, request.password)
            AppContainer.auditLogService.record(userId, "user_activated", "user", userId)
            call.respond(HttpStatusCode.NoContent)
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("ACTIVATION_ERROR", exception.message ?: "Activation failed."))
        }
    }
}
