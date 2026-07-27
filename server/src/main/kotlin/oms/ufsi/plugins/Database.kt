package oms.ufsi.plugins

import io.ktor.server.application.*
import oms.ufsi.database.tables.HealthChecksTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Налаштовує роботу з базою даних.
 */
fun Application.configureDatabase() {

    val url = environment.config.property("database.url").getString()
    val driver = environment.config.property("database.driver").getString()
    val user = environment.config.property("database.user").getString()
    val password = environment.config.property("database.password").getString()

    /**
     * Реєструємо підключення до MySQL.
     */
    Database.connect(
        url = url,
        driver = driver,
        user = user,
        password = password
    )

    /**
     * Виконуємо службові дії в транзакції.
     *
     * Будь-яка зміна даних або структури БД
     * в Exposed повинна виконуватися всередині transaction {}.
     */
    transaction {
        createSchema()
    }

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