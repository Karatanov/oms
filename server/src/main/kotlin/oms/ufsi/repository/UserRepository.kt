package oms.ufsi.repository

import oms.ufsi.domain.User

/**
 * Контракт доступу до користувачів системи.
 */
interface UserRepository {

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
        roleId: Long
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
}