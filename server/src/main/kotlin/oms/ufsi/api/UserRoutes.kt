package oms.ufsi.api

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
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.CreateUserRequest
import oms.ufsi.dto.ErrorResponse
import oms.ufsi.dto.toResponse

/**
 * Маршрути роботи з користувачами.
 */
fun Route.userRoutes() {

    val userService = AppContainer.userService

    /**
     * Повертає всіх користувачів системи.
     */
    get("/api/v1/users") {

        val response = userService
            .getAllUsers()
            .map { user ->

                user.toResponse()
            }

        call.respond(response)
    }

    /**
     * Створює нового користувача.
     */
    post("/api/v1/users") {

        val request =
            call.receive<CreateUserRequest>()

        try {

            val user = userService.createUser(
                username = request.username,
                email = request.email,
                password = request.password,
                roleCode = request.roleCode
            )

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
}