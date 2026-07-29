package oms.ufsi.domain

import kotlinx.serialization.Serializable

/**
 * Доменна модель користувача.
 *
 * Доменна модель не повинна залежати
 * від способу зберігання даних.
 */
@Serializable
data class User(

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
     * Повна інформація про роль користувача.
     *
     * Ми навмисно повертаємо не roleId,
     * а готовий доменний об'єкт, щоб API
     * було зручніше використовувати клієнтам.
     */
    val role: Role
)