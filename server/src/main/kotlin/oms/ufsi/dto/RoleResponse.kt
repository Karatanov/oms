package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * DTO-відповідь для ролі користувача.
 *
 * DTO (Data Transfer Object) описує формат даних,
 * який повертається через REST API.
 */
@Serializable
data class RoleResponse(

    /**
     * Унікальний ідентифікатор ролі.
     */
    val id: Long,

    /**
     * Системний код ролі.
     */
    val code: String,

    /**
     * Назва ролі для відображення користувачу.
     */
    val name: String
)