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
}