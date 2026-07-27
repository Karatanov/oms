package oms.ufsi.domain

import kotlinx.serialization.Serializable

/**
 * Доменна модель ролі користувача.
 *
 * Доменні моделі описують предметну область
 * і не повинні залежати від деталей зберігання даних.
 */
@Serializable
data class Role(

    /**
     * Унікальний ідентифікатор ролі.
     */
    val id: Long,

    /**
     * Системний код ролі.
     */
    val code: String,

    /**
     * Назва ролі.
     */
    val name: String
)