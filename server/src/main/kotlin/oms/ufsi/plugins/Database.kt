package oms.ufsi.plugins

import io.ktor.server.application.*
import oms.ufsi.database.tables.HealthChecksTable
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

/**
 * Налаштовує роботу з базою даних.
 */
fun Application.configureDatabase() {

    val url = environment.config.property("database.url").getString()
    val driver = environment.config.property("database.driver").getString()
    val user = environment.config.property("database.user").getString()
    val password = environment.config.property("database.password").getString()

    /**
     * Спочатку виконуємо всі SQL-міграції.
     *
     * Після завершення роботи Flyway структура БД
     * гарантовано відповідає поточній версії застосунку.
     */
    Flyway
        .configure()
        .dataSource(url, user, password)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .dataSource(url, user, password)
        .load()
        .migrate()

    /**
     * Реєструємо підключення Exposed.
     *
     * Надалі всі запити до БД виконуватимуться
     * через цей механізм.
     */
    Database.connect(
        url = url,
        driver = driver,
        user = user,
        password = password
    )

    log.info("База даних успішно ініціалізована.")
}

/**
 * Створює службові таблиці застосунку.
 */
private fun createSchema() {

    /**
     * Якщо таблиця вже існує,
     * Exposed не буде створювати її повторно.
     */
    SchemaUtils.create(HealthChecksTable)
}