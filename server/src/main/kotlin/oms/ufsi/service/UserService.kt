package oms.ufsi.service

import oms.ufsi.domain.User
import oms.ufsi.repository.UserRepository

/**
 * Сервіс роботи з користувачами.
 *
 * Саме тут повинні знаходитися
 * бізнес-правила, пов'язані з користувачами.
 */
class UserService(
    private val userRepository: UserRepository
) {

    /**
     * Повертає всіх користувачів системи.
     */
    fun getAllUsers(): List<User> {

        return userRepository.findAll()
    }
}