package oms.umitaf.dto

import kotlinx.serialization.Serializable

/**
 * Відповідь після успішної автентифікації.
 *
 * The browser also receives a secure session cookie; API/mobile clients can
 * use the short-lived bearer token.
 */
@Serializable
data class LoginResponse(

    /**
     * Інформація про поточного користувача.
     */
    val user: UserResponse,
    val accessToken: String
)
