package oms.umitaf.api

/**
 * Архітектурне правило проєкту:
 *
 * Route  -> приймає HTTP-запит.
 * Service -> виконує бізнес-логіку.
 * Repository -> працює з даними.
 *
 * Кожен шар відповідає лише за свою область
 * відповідальності.
 *
 * /**
 *  * Правило проєкту:
 *  *
 *  * Маршрут відповідає лише за:
 *  * - HTTP-параметри;
 *  * - коди відповіді;
 *  * - виклик сервісів.
 *  *
 *  * Будь-яка бізнес-логіка повинна
 *  * розташовуватися в Service-класах.
 *  */
 */
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.ErrorResponse
import oms.umitaf.dto.toResponse

/**
 * Маршрути для роботи з ролями користувачів.
 */
fun Route.roleRoutes() {

    /**
     * Отримуємо сервіс із контейнера залежностей.
     */
    val roleService = AppContainer.roleService

    /**
     * Повертає всі ролі системи.
     */
    get("/api/v1/roles") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get

        call.respond(
            roleService
                .getAllRoles()
                .filterNot { it.code.equals("GUEST", ignoreCase = true) }
                .map { role ->

                    role.toResponse()
                }
        )
    }
    /**
     * Повертає інформацію про конкретну роль.
     *
     * Приклад:
     * GET /api/v1/roles/ADMIN
     */
    get("/api/v1/roles/{code}") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get

        val code = call.parameters["code"]

        if (code == null) {

            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "INVALID_ROLE_CODE",
                    message = "Код ролі не передано."
                )
            )

            return@get
        }

        val role = roleService.getRoleByCode(code)

        if (role == null) {

            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = "ROLE_NOT_FOUND",
                    message = "Роль '$code' не знайдена."
                )
            )

            return@get
        }

        call.respond(
            role.toResponse()
        )
    }
}
