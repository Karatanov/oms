package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * Запит на вхід до системи.
 */
@Serializable
data class LoginRequest(

    /**
     * Логін користувача.
     */
    val username: String,

    /**
     * Пароль користувача.
     */
    val password: String
)

@Serializable
data class ActivateAccountRequest(val token: String, val password: String)
