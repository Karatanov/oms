package oms.ufsi

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import oms.ufsi.api.healthRoutes

/**
 * Точка входу в серверний застосунок.
 *
 * Саме із цієї функції починається запуск backend-серва.
 */
fun main() {

    // Створюємо HTTP-сервер на базі рушія Netty.
    embeddedServer(
        factory = Netty,
        port = _root_ide_package_.oms.ufsi.SERVER_PORT,
        host = "0.0.0.0",

        // Після запуску Ktor викличе функцію module()
        // і передасть їй об'єкт Application для налаштування застосунку.
        module = Application::module
    ).start(wait = true)
}

/**
 * Основна конфігурація застосунку.
 *
 * У майбутньому тут будуть підключатися:
 * - база даних;
 * - авторизація;
 * - DI-контейнер;
 * - логування;
 * - маршрути API.
 */
fun Application.module() {

    install(ContentNegotiation) {
        json()
    }

    routing {

        // Реєструємо службові маршрути системи.
        healthRoutes()
    }
}