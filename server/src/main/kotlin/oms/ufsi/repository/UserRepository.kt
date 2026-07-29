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
}