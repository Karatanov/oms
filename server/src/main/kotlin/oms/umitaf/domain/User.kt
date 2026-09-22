package oms.umitaf.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

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
    val role: Role,
    val firstName: String = "",
    val lastName: String = "",
    val status: String = "active",
    val region: String? = null,
    val department: String? = null,
    val preferredLang: String = "uk",
    @Contextual val lastLoginAt: java.time.LocalDateTime? = null,
    val failedLoginCount: Int = 0,
    @Contextual val lockedUntil: java.time.LocalDateTime? = null,
    @Contextual val createdAt: java.time.LocalDateTime? = null,
    @Contextual val updatedAt: java.time.LocalDateTime? = null
)
