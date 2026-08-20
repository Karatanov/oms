package oms.ufsi.repository

import oms.ufsi.domain.User

/**
 * Контракт доступу до користувачів системи.
 */
interface UserRepository {

    data class AuthenticationState(
        val status: String,
        val failedLoginCount: Int,
        val lockedUntil: java.time.LocalDateTime?
    )

    data class ActivationState(val userId: Long, val expiresAt: java.time.LocalDateTime?)

    /**
     * Повертає всіх користувачів.
     */
    fun findAll(): List<User>

    /**
     * Створює нового користувача.
     */
    fun create(
        username: String,
        email: String,
        password: String,
        roleId: Long,
        firstName: String,
        lastName: String,
        status: String,
        region: String?,
        department: String?,
        preferredLang: String
    ): User

    /**
     * Повертає користувача за логіном.
     *
     * Якщо користувача не знайдено,
     * повертається null.
     */
    fun findByUsername(username: String): User?

    /**
     * Перевіряє існування користувача
     * з указаним логіном.
     */
    fun existsByUsername(
        username: String
    ): Boolean

    /**
     * Перевіряє існування користувача
     * з указаною електронною поштою.
     */
    fun existsByEmail(
        email: String
    ): Boolean

    fun update(id: Long, username: String, email: String, passwordHash: String, roleId: Long, firstName: String, lastName: String, status: String, region: String?, department: String?, preferredLang: String): User?

    fun delete(id: Long): Boolean

    fun authenticationState(userId: Long): AuthenticationState?

    fun recordFailedLogin(userId: Long, lockedUntil: java.time.LocalDateTime?)

    fun recordSuccessfulLogin(userId: Long, at: java.time.LocalDateTime)

    fun storeActivationToken(userId: Long, tokenHash: String, expiresAt: java.time.LocalDateTime)

    fun activationState(tokenHash: String): ActivationState?

    fun activate(userId: Long, passwordHash: String, at: java.time.LocalDateTime): Boolean
}
