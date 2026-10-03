package oms.umitaf.dto

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
    val status: String,
    /** Immutable build metadata injected by the production Docker build. */
    val gitSha: String,
    val buildTime: String
)
