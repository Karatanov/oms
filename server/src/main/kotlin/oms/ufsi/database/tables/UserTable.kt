package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Опис таблиці користувачів.
 *
 * Повинен відповідати структурі,
 * визначеній у Flyway-міграції.
 */
object UserTable : LongIdTable("users") {

    /**
     * Логін користувача.
     */
    val username =
        varchar("username", 100)
            .uniqueIndex()

    /**
     * Email користувача.
     */
    val email =
        varchar("email", 255)
            .uniqueIndex()

    /**
     * Пароль користувача.
     *
     * Зараз використовується лише
     * для навчальних цілей.
     */
    val passwordHash =
        varchar("password_hash", 255)

    /**
     * Посилання на роль користувача.
     */
    val roleId =
        reference(
            name = "role_id",
            foreign = RoleTable,
            onDelete = ReferenceOption.RESTRICT
        )
}
