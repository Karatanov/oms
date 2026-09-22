package oms.umitaf.dto

import kotlinx.serialization.Serializable

/**
 * DTO для створення нового користувача.
 *
 * Вхідні DTO описують формат даних,
 * які надсилає клієнт.
 */
@Serializable
data class CreateUserRequest(

    /**
     * Логін користувача.
     */
    val username: String,

    /**
     * Email користувача.
     */
    val email: String,

    /**
     * Пароль користувача.
     *
     * Поки що зберігається без хешування.
     */
    val password: String = "",

    /**
     * Код ролі користувача.
     *
     * Наприклад:
     * ADMIN
     * INSPECTOR
     */
    val roleCode: String,
    val firstName: String = "",
    val lastName: String = "",
    val status: String = "active",
    val region: String? = null,
    val department: String? = null,
    val preferredLang: String = "uk"
)
