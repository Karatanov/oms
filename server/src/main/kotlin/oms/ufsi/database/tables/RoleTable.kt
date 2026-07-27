package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Опис таблиці ролей користувачів.
 *
 * Exposed використовує цей клас для формування
 * SQL-запитів та мапінгу даних між Kotlin і MySQL.
 *
 * Структура таблиці повинна відповідати
 * Flyway-міграції V2__create_roles.sql.
 */
object RoleTable : LongIdTable("roles") {

    /**
     * Системний код ролі.
     *
     * Приклади:
     * - ADMIN
     * - PROJECT_MANAGER
     * - INSPECTOR
     */
    val code = varchar("code", 50).uniqueIndex()

    /**
     * Назва ролі для відображення користувачу.
     */
    val name = varchar("name", 100)
}