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
    private val userRepository: UserRepository,
    private val roleService: RoleService
) {

    /**
     * Повертає всіх користувачів системи.
     */
    fun getAllUsers(): List<User> {

        return userRepository.findAll()
    }

    /**
     * Створює нового користувача.
     */
    fun createUser(
        username: String,
        email: String,
        password: String,
        roleCode: String
    ): User {

        val role = roleService.getRoleByCode(roleCode)
            ?: throw IllegalArgumentException(
                "Роль '$roleCode' не існує."
            )

        return userRepository.create(
            username = username,
            email = email,
            password = password,
            roleId = role.id
        )
    }
}