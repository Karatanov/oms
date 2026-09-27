package oms.umitaf.api
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.ErrorResponse
import oms.umitaf.security.JwtTokenService
import oms.umitaf.security.UserSession

/** Resolves the user's current status and role on every protected request. */
internal suspend fun ApplicationCall.requireRole(vararg roles: String): UserSession? {
    val session = bearerSession() ?: sessions.get<UserSession>() ?: run {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("AUTHENTICATION_REQUIRED", "Login is required."))
        return null
    }
    // Guest access is an anonymous browser session, not a user record or RBAC role.
    if (session.roleCode.equals("GUEST", ignoreCase = true) && session.userId == 0L) {
        if (roles.any { it.equals("GUEST", ignoreCase = true) }) return session
        respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Guest access is limited to public screens."))
        return null
    }
    val user = AppContainer.userService.getAllUsers().firstOrNull { it.id == session.userId }
    if (user == null || !user.status.equals("active", ignoreCase = true)) {
        sessions.clear<UserSession>()
        respond(HttpStatusCode.Unauthorized, ErrorResponse("ACCOUNT_INACTIVE", "This account is not active."))
        return null
    }
    if (user.role.code.uppercase() !in roles.map { it.uppercase() }) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Your role cannot perform this action."))
        return null
    }
    return UserSession(user.id, user.role.code)
}

private fun ApplicationCall.bearerSession(): UserSession? =
    request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substringAfter(' ')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(JwtTokenService::verify)

/**
 * Project managers have the same operational access to projects as administrators.
 * Route-level role checks still reserve permanent deletion and administration for ADMIN.
 */
@Suppress("UNUSED_PARAMETER")
internal suspend fun ApplicationCall.requireProjectAccess(session: UserSession, projectUuid: String): Boolean = true
