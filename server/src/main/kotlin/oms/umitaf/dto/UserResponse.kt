package oms.umitaf.dto

import kotlinx.serialization.Serializable

/**
 * DTO-відповідь для користувача.
 *
 * Пароль навмисно відсутній,
 * щоб його неможливо було випадково
 * повернути клієнту.
 */
@Serializable
data class UserResponse(

    /**
     * Унікальний ідентифікатор користувача.
     */
    val id: Long,

    /**
     * Логін користувача.
     */
    val username: String,

    /**
     * Email користувача.
     */
    val email: String,

    val firstName: String,
    val lastName: String,
    val status: String,
    val region: String?,
    val department: String?,
    val preferredLang: String,
    val lastLoginAt: String?,
    val failedLoginCount: Int,
    val lockedUntil: String?,
    val createdAt: String?,
    val updatedAt: String?,

    /**
     * Інформація про роль користувача.
     */
    val role: RoleResponse
)
