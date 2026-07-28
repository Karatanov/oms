package oms.ufsi.domain

/**
 * Доменна модель користувача.
 *
 * Доменна модель не повинна залежати
 * від способу зберігання даних.
 */
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
     * Роль користувача.
     */
    val role: Role
)