package oms.urp3

import io.ktor.server.application.*
import oms.urp3.config.AppContainer
import oms.urp3.plugins.*

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

    configureSecurityHeaders()

    /**
     * Налаштовуємо підтримку JSON.
     */
    configureSerialization()

    // Large dynamic registry responses benefit from HTTP gzip compression.
    configureCompression()

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
        configureProcurementAutoRefresh()
        configureSubprojectAutoRefresh()
    }

    /**
     * Реєструємо HTTP-маршрути застосунку.
     */
    configureRouting()
}

/** Approved procurement data are imported periodically.  It is deliberately
 * configured after the database migrations and does not block application
 * start or browser navigation. */
private fun Application.configureProcurementAutoRefresh() {
    if (System.getenv("PROCUREMENT_AUTO_REFRESH_ENABLED").equals("false", ignoreCase = true)) return
    val initialDelay = System.getenv("PROCUREMENT_AUTO_REFRESH_INITIAL_DELAY_MINUTES")
        ?.toLongOrNull()?.coerceAtLeast(1) ?: 5L
    val intervalHours = System.getenv("PROCUREMENT_AUTO_REFRESH_INTERVAL_HOURS")
        ?.toLongOrNull()?.coerceAtLeast(1) ?: 24L
    AppContainer.procurementService.startAutomaticApprovedSourceRefresh(initialDelay, intervalHours)
    environment.monitor.subscribe(ApplicationStopping) {
        AppContainer.procurementService.stopAutomaticApprovedSourceRefresh()
    }
}

/** The approved public monitoring workbook is refreshed off the startup path. */
private fun Application.configureSubprojectAutoRefresh() {
    if (System.getenv("SUBPROJECT_AUTO_REFRESH_ENABLED").equals("false", ignoreCase = true)) return
    val initialDelay = System.getenv("SUBPROJECT_AUTO_REFRESH_INITIAL_DELAY_MINUTES")
        ?.toLongOrNull()?.coerceAtLeast(1) ?: 10L
    val intervalHours = System.getenv("SUBPROJECT_AUTO_REFRESH_INTERVAL_HOURS")
        ?.toLongOrNull()?.coerceAtLeast(1) ?: 24L
    AppContainer.subprojectRegistryRefreshService.startAutomaticSourceRefresh(initialDelay, intervalHours)
    environment.monitor.subscribe(ApplicationStopping) {
        AppContainer.subprojectRegistryRefreshService.stopAutomaticSourceRefresh()
    }
}
