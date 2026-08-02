package oms.ufsi

import io.ktor.server.application.*
import oms.ufsi.plugins.*

/**
 * Точка входу в серверний застосунок.
 *
 * EngineMain автоматично:
 * - зчитує application.yaml;
 * - створює HTTP-сервер;
 * - викликає функцію module();
 * - передає їй об'єкт Application.
 *
 * Завдяки цьому параметри запуску не потрібно
 * дублювати безпосередньо в Kotlin-коді.
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

/**
 * Головний модуль застосунку.
 *
 * Саме ця функція викликається Ktor після
 * завершення початкової ініціалізації сервера.
 *
 * Порядок виклику конфігураційних функцій важливий:
 * 1. Логування.
 * 2. Серіалізація JSON.
 * 3. Обробка помилок.
 * 4. Реєстрація HTTP-маршрутів.
 */
fun Application.module(
    configureDatabase: Boolean = true
) {

    /**
     * Налаштовуємо журналювання роботи сервера.
     */
    configureMonitoring()

    /**
     * Налаштовуємо підтримку JSON.
     */
    configureSerialization()

    configureCors()

    configureSessions()

    /**
     * Налаштовуємо єдиний механізм обробки помилок.
     */
    configureErrorHandling()

    /**
     * Перевіряємо доступність бази даних
     * під час запуску застосунку.
     */
    if (configureDatabase) {
        configureDatabase()
    }

    /**
     * Реєструємо HTTP-маршрути застосунку.
     */
    configureRouting()
}
