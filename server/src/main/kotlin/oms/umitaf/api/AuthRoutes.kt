package oms.umitaf.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.ErrorResponse
import oms.umitaf.dto.LoginRequest
import oms.umitaf.dto.ActivateAccountRequest
import oms.umitaf.dto.PasswordResetRequest
import oms.umitaf.dto.LoginResponse
import oms.umitaf.dto.toResponse
import oms.umitaf.security.UserSession
import oms.umitaf.security.JwtTokenService
import oms.umitaf.service.AccountLockedException

/**
 * Маршрути автентифікації.
 */
fun Route.authRoutes() {

    val authService = AppContainer.authService

    /** Restores a validated bearer or browser cookie session after a page refresh. */
    get("/api/v1/auth/session") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get
        if (session.roleCode.equals("GUEST", ignoreCase = true)) {
            return@get call.respond(
                GuestSessionResponse(guest = true)
            )
        }
        val user = AppContainer.userService.getAllUsers().firstOrNull { it.id == session.userId }
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        call.respond(user.toResponse())
    }

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
                    accessToken = JwtTokenService.issue(user.id, user.role.code),
                    browserBearerSupported = true
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

    post("/api/v1/auth/guest") {
        call.sessions.set(UserSession(0L, "GUEST"))
        call.respond(GuestSessionResponse(guest = true, accessToken = JwtTokenService.issue(0L, "GUEST")))
    }

    post("/api/v1/auth/password-reset") {
        val identifier = call.receive<PasswordResetRequest>().identifier.trim()
        if (identifier.isNotEmpty()) {
            AppContainer.userService.getAllUsers().firstOrNull {
                it.username.equals(identifier, ignoreCase = true) || it.email.equals(identifier, ignoreCase = true)
            }?.let { user -> AppContainer.activationService.issuePasswordReset(user.id, user.email) }
        }
        // Do not reveal whether an account with this identifier exists.
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

@kotlinx.serialization.Serializable
private data class GuestSessionResponse(
    val guest: Boolean, val username: String = "Guest", val roleCode: String = "GUEST",
    val accessToken: String? = null
)
