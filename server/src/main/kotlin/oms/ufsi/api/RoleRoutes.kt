package oms.ufsi.api

/**
 * Архітектурне правило проєкту:
 *
 * Route  -> приймає HTTP-запит.
 * Service -> виконує бізнес-логіку.
 * Repository -> працює з даними.
 *
 * Кожен шар відповідає лише за свою область
 * відповідальності.
 */
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer

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

        call.respond(
            roleService.getAllRoles()
        )
    }
}