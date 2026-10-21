package oms.urp3.dto

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

    /** Initial password. It is accepted only in transit and hashed by the server. */
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
