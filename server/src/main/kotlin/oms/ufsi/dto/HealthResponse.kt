package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * Відповідь сервера на запит перевірки працездатності.
 *
 * Анотація @Serializable дозволяє Ktor автоматично
 * перетворювати Kotlin-об'єкт у JSON.
 */
@Serializable
data class HealthResponse(

    /**
     * Поточний стан системи.
     *
     * Можливі значення в майбутньому:
     * - UP
     * - DEGRADED
     * - DOWN
     */
    val status: String
)