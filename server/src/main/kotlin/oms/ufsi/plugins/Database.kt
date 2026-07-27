package oms.ufsi.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Налаштовує роботу з базою даних.
 */
fun Application.configureDatabase() {

    val url = environment.config.property("database.url").getString()
    val driver = environment.config.property("database.driver").getString()
    val user = environment.config.property("database.user").getString()
    val dbPassword = environment.config.property("database.password").getString()

    val maxPoolSize =
        environment.config.property("database.pool.maxSize").getString().toInt()

    val minIdle =
        environment.config.property("database.pool.minIdle").getString().toInt()

    /**
     * Спочатку оновлюємо структуру БД.
     *
     * Після завершення роботи Flyway застосунок
     * гарантовано працює з актуальною схемою даних.
     */
    Flyway
        .configure()
        .dataSource(url, user, dbPassword)
        .load()
        .migrate()

    /**
     * Створюємо конфігурацію пулу з'єднань.
     */
    val hikariConfig = HikariConfig().apply {

        jdbcUrl = url
        driverClassName = driver

        username = user
        password = dbPassword

        maximumPoolSize = maxPoolSize
        minimumIdle = minIdle

        /**
         * Ім'я пулу, яке буде видно в логах
         * та інструментах моніторингу.
         */
        poolName = "USIF-Pool"
    }

    /**
     * Створюємо пул з'єднань.
     */
    val dataSource = HikariDataSource(hikariConfig)

    /**
     * Exposed працює не напряму з MySQL,
     * а через пул HikariCP.
     *
     * Саме пул відповідає за повторне використання
     * та життєвий цикл фізичних з'єднань.
     */
    Database.connect(dataSource)

    log.info("База даних успішно ініціалізована.")
}