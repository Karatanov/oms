package oms.ufsi.dto

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

    /**
     * Інформація про роль користувача.
     */
    val role: RoleResponse
)