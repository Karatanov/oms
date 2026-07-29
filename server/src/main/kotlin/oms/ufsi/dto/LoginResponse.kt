package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * Відповідь після успішної автентифікації.
 *
 * JWT-токен буде додано на наступній ітерації.
 */
@Serializable
data class LoginResponse(

    /**
     * Інформація про поточного користувача.
     */
    val user: UserResponse
)