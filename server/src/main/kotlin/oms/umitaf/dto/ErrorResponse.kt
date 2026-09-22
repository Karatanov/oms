package oms.umitaf.dto

import kotlinx.serialization.Serializable

/**
 * Стандартна відповідь API у випадку помилки.
 *
 * Єдиний формат відповідей значно спрощує
 * роботу frontend-розробників та мобільних клієнтів.
 */
@Serializable
data class ErrorResponse(

    /**
     * Короткий код помилки.
     *
     * Приклади:
     * - NOT_FOUND
     * - VALIDATION_ERROR
     * - UNAUTHORIZED
     */
    val error: String,

    /**
     * Детальний опис проблеми.
     */
    val message: String
)