package oms.umitaf.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/**
 * Налаштовує серіалізацію та десеріалізацію JSON.
 *
 * Завдяки цьому Ktor може автоматично:
 * - перетворювати Kotlin-об'єкти у JSON;
 * - створювати Kotlin-об'єкти з JSON-запитів клієнта.
 */
fun Application.configureSerialization() {

    install(ContentNegotiation) {

        /**
         * Використовуємо kotlinx.serialization як основний
         * механізм роботи з JSON.
         *
         * Пізніше тут можна буде додати:
         * - prettyPrint;
         * - налаштування дат;
         * - ігнорування невідомих полів;
         * - інші параметри серіалізації.
         */
        json(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }
}
