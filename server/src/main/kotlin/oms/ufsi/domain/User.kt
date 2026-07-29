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
     * BCrypt-хеш пароля.
     *
     * Це поле використовується лише
     * всередині backend і ніколи не повинно
     * повертатися через REST API.
     */
    val passwordHash: String,
    
    /**
     * Повна інформація про роль користувача.
     *
     * Ми навмисно повертаємо не roleId,
     * а готовий доменний об'єкт, щоб API
     * було зручніше використовувати клієнтам.
     */
    val role: Role
)