package oms.ufsi

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import oms.ufsi.plugins.configureErrorHandling
import oms.ufsi.plugins.configureRouting
import oms.ufsi.plugins.configureSerialization

/**
 * Точка входу в серверний застосунок.
 *
 * Саме із цієї функції починається запуск backend-серва.
 */
/**
 * Точка входу в серверний застосунок.
 *
 * Основні параметри запуску (порт, модулі тощо)
 * тепер зчитуються з application.yaml.
 */
fun main() {

    embeddedServer(
        factory = Netty,

        /**
         * Значення 0 означає:
         * "використати конфігурацію з application.yaml".
         *
         * Якщо вказати конкретне число, воно матиме
         * пріоритет над конфігураційним файлом.
         */
        port = 0,

        host = "0.0.0.0",
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

    /**
     * Налаштовуємо підтримку JSON.
     */
    configureSerialization()

    /**
     * Налаштовуємо єдиний механізм обробки помилок.
     */
    configureErrorHandling()

    /**
     * Реєструємо HTTP-маршрути застосунку.
     */
    configureRouting()
}