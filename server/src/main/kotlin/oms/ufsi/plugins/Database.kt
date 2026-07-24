package oms.ufsi.plugins

import io.ktor.server.application.*
import java.sql.DriverManager

/**
 * Перевіряє можливість підключення до бази даних.
 *
 * Поки що ми лише відкриваємо та закриваємо з'єднання.
 * Це дозволяє переконатися, що:
 * - Docker-контейнер працює;
 * - параметри підключення правильні;
 * - JDBC-драйвер успішно підключений.
 */
fun Application.configureDatabase() {

    val url = environment.config.property("database.url").getString()
    val user = environment.config.property("database.user").getString()
    val password = environment.config.property("database.password").getString()

    /**
     * Використовуємо конструкцію use {},
     * щоб з'єднання гарантовано закрилося
     * навіть у випадку помилки.
     */
    DriverManager.getConnection(url, user, password).use {

        log.info("Успішне підключення до MySQL.")
    }
}