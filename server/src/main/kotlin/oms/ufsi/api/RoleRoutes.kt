package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.repository.ExposedRoleRepository

/**
 * Маршрути для роботи з ролями користувачів.
 */
fun Route.roleRoutes() {

    val repository = ExposedRoleRepository()

    /**
     * Повертає всі ролі системи.
     */
    get("/api/v1/roles") {

        call.respond(repository.findAll())
    }
}