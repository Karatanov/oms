package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.domain.User

/**
 * Маршрути роботи з користувачами.
 */
fun Route.userRoutes() {

    val userService = AppContainer.userService

    /**
     * Повертає всіх користувачів системи.
     */
    get("/api/v1/users") {

        call.respond<List<User>>(
            userService.getAllUsers()
        )
    }
}