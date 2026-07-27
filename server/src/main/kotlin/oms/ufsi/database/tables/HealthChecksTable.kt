package oms.ufsi.database.tables

/**
 * Навчальний приклад опису таблиці через Exposed.
 *
 * Реальне створення таблиць виконується Flyway-міграціями.
 *
 * Файл буде видалено після появи перших
 * бізнес-сутностей системи.
 */

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * Навчальна таблиця для перевірки роботи Exposed.
 *
 * Пізніше вона буде видалена після підключення Flyway
 * та створення реальних таблиць предметної області.
 */
object HealthChecksTable : LongIdTable("health_checks") {

    /**
     * Час створення запису.
     */
    val createdAt = timestamp("created_at")
}