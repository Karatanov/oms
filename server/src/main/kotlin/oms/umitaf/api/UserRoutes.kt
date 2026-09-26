package oms.umitaf.api

/**
 * Відповідальність Route:
 *
 * - отримати HTTP-запит;
 * - перетворити JSON у DTO;
 * - викликати сервіс;
 * - сформувати HTTP-відповідь.
 *
 * Валідація бізнес-правил повинна
 * виконуватися у Service-класах.
 */
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.CreateUserRequest
import oms.umitaf.dto.ErrorResponse
import oms.umitaf.dto.UpdateUserRequest
import oms.umitaf.dto.toResponse
import java.util.UUID

/**
 * Маршрути роботи з користувачами.
 */
fun Route.userRoutes() {

    val userService = AppContainer.userService

    /**
     * Повертає всіх користувачів системи.
     */
    get("/api/v1/users") {
        call.requireRole("ADMIN") ?: return@get

        val archive = call.request.queryParameters["archive"]?.lowercase()
        val response = userService.getAllUsers()
            .filter { when (archive) { "archived" -> it.isArchived; "all" -> true; else -> !it.isArchived } }
            .map { user ->

                user.toResponse()
            }

        call.respond(response)
    }

    /**
     * Створює нового користувача.
     */
    /**
     * Архітектурне правило:
     *
     * Route не перевіряє бізнес-обмеження.
     *
     * Його завдання:
     * - прийняти HTTP-запит;
     * - перетворити JSON у DTO;
     * - викликати сервіс;
     * - повернути HTTP-відповідь.
     *
     * Уся валідація предметної області
     * виконується у Service-шарі.
     */
    post("/api/v1/users") {
        val session = call.requireRole("ADMIN") ?: return@post

        val request =
            call.receive<CreateUserRequest>()

        try {

            require(!request.roleCode.equals("GUEST", ignoreCase = true)) { "Guest access is not a user role." }

            val user = userService.createUser(
                username = request.username,
                email = request.email,
                // Pending accounts receive their actual password only through the activation flow.
                // The random placeholder is never exposed and is replaced on activation.
                password = "Activation1${UUID.randomUUID()}",
                roleCode = request.roleCode,
                firstName = request.firstName, lastName = request.lastName, status = "pending",
                region = request.region, department = request.department, preferredLang = request.preferredLang
            )
            AppContainer.auditLogService.record(
                session.userId, "user_created", "user", user.id,
                newValues = "{\"role\":\"${user.role.code}\",\"status\":\"pending\"}"
            )
            AppContainer.activationService.issue(user.id, user.email)

            call.respond(
                HttpStatusCode.Created,
                user.toResponse()
            )

        } catch (exception: IllegalArgumentException) {

            call.respond(
                HttpStatusCode.BadRequest,

                ErrorResponse(
                    error = "VALIDATION_ERROR",
                    message = exception.message
                        ?: "Помилка валідації."
                )
            )
        }
    }

    patch("/api/v1/users/{id}") {
        val session = call.requireRole("ADMIN") ?: return@patch
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "User ID is required."))
        try {
            val request = call.receive<UpdateUserRequest>()
            require(!request.roleCode.equals("GUEST", ignoreCase = true)) { "Guest access is not a user role." }
            val before = userService.getAllUsers().firstOrNull { it.id == id }
            val user = userService.updateUser(id, request)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found."))
            val action = when {
                before?.status != user.status -> "user_status_changed"
                before?.role?.code != user.role.code -> "user_role_changed"
                !request.password.isNullOrBlank() -> "user_password_reset"
                else -> "user_updated"
            }
            AppContainer.auditLogService.record(
                session.userId, action, "user", user.id,
                oldValues = "{\"role\":\"${before?.role?.code.orEmpty()}\",\"status\":\"${before?.status.orEmpty()}\"}",
                newValues = "{\"role\":\"${user.role.code}\",\"status\":\"${user.status}\"}"
            )
            call.respond(user.toResponse())
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid user data."))
        }
    }

    post("/api/v1/users/{id}/archive") {
        val session = call.requireRole("ADMIN") ?: return@post
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "User ID is required."))
        if (id == session.userId) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "You cannot archive your own account."))
        val target = userService.getAllUsers().firstOrNull { it.id == id }
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found."))
        if (!userService.archiveUser(id, session.userId)) return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found."))
        AppContainer.auditLogService.record(session.userId, "user_archived", "user", id, oldValues = "{\"username\":\"${target.username}\"}")
        call.respond(HttpStatusCode.NoContent)
    }

    post("/api/v1/users/{id}/restore") {
        val session = call.requireRole("ADMIN") ?: return@post
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "User ID is required."))
        val target = userService.getAllUsers().firstOrNull { it.id == id }
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found."))
        if (!userService.restoreUser(id)) return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found."))
        AppContainer.auditLogService.record(session.userId, "user_restored", "user", id, oldValues = "{\"username\":\"${target.username}\"}")
        call.respond(HttpStatusCode.NoContent)
    }

    get("/api/v1/users/{id}/permanent-delete-dependencies") {
        call.requireRole("ADMIN") ?: return@get
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "User ID is required."))
        call.respond(userService.userDependencies(id))
    }

    delete("/api/v1/users/{id}/permanent") {
        val session = call.requireRole("ADMIN") ?: return@delete
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "User ID is required."))
        if (id == session.userId) return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "You cannot delete your own account."))
        val target = userService.getAllUsers().firstOrNull { it.id == id }
            ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found."))
        val dependencies = userService.userDependencies(id)
        if (dependencies.isNotEmpty()) return@delete call.respond(HttpStatusCode.Conflict, ErrorResponse("HAS_DEPENDENCIES", dependencies.entries.joinToString("; ") { "${it.value} ${it.key}" }))
        if (!userService.permanentlyDeleteUser(id)) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found."))
        AppContainer.auditLogService.record(
            session.userId, "permanent_delete", "user", id,
            oldValues = "{\"username\":\"${target.username}\",\"role\":\"${target.role.code}\"}"
        )
        call.respond(HttpStatusCode.NoContent)
    }
}
