package oms.urp3.plugins

import oms.urp3.config.Deployment
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import oms.urp3.config.AppContainer
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
    val dialect = environment.config.property("database.dialect").getString().lowercase()

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
    if (!Deployment.legacy) {
        require(dialect == "mysql" && user.isNotBlank() && dbPassword.isNotBlank())
        require(!System.getenv("DATABASE_USER").isNullOrBlank() && !System.getenv("DATABASE_URL").isNullOrBlank())
        require(url.startsWith("jdbc:mysql://") && java.net.URI(url.removePrefix("jdbc:")).path == "/oms_" + Deployment.brand) {
            "The isolated deployment requires its own named MySQL database."
        }
        require((System.getenv("OMS_JWT_SECRET")?.length ?: 0) >= 32)
        require(!System.getenv("OMS_UPLOAD_DIR").isNullOrBlank())
    }
    val history = when {
        !Deployment.legacy -> "classpath:db/isolated"
        dialect == "mysql" -> "classpath:db/migration"
        dialect == "tidb" -> "classpath:db/migration-tidb"
        else -> error("Unsupported database dialect: $dialect")
    }
    val flyway = Flyway
        .configure()
        .dataSource(url, user, dbPassword)
        .locations(history, "classpath:db/common")
        .load()

    // Opt-in recovery for a locally interrupted development migration.
    // Production starts never repair history implicitly.
    if (
        Deployment.legacy && (System.getProperty("oms.flywayRepair") == "true" ||
        System.getenv("OMS_FLYWAY_REPAIR").equals("true", ignoreCase = true))
    ) {
        log.warn("Flyway repair was explicitly requested for this startup.")
        flyway.repair()
    }

    flyway
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
        poolName = "UR III-Pool"
    }

    /**
     * Створюємо пул з'єднань.
     */
    val dataSource = HikariDataSource(hikariConfig)

    DatabaseReadiness.register(dataSource)
    environment.monitor.subscribe(ApplicationStopping) {
        DatabaseReadiness.unregister(dataSource)
        dataSource.close()
    }

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
