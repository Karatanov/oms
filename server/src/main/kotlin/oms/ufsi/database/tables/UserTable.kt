package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * Опис таблиці користувачів.
 *
 * Повинен відповідати структурі,
 * визначеній у Flyway-міграції.
 */
object UserTable : LongIdTable("users") {

    /** Public identifier required by the current database schema. */
    val uuid = varchar("uuid", 36).uniqueIndex()
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)

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

    val status = varchar("status", 20)
    val region = varchar("region", 100).nullable()
    val department = varchar("department", 100).nullable()
    val preferredLang = varchar("preferred_lang", 2)
    val lastLoginAt = datetime("last_login_at").nullable()
    val failedLoginCount = integer("failed_login_count")
    val lockedUntil = datetime("locked_until").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
