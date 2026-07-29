package oms.ufsi.service

import oms.ufsi.domain.User
import oms.ufsi.repository.UserRepository
import oms.ufsi.security.PasswordHasher

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
        /**
         * Спочатку перевіряємо коректність
         * отриманих від клієнта даних.
         */
        validateUserData(
            username = username,
            email = email,
            password = password
        )
        /**
         * Пароль ніколи не передається
         * до репозиторію у відкритому вигляді.
         */
        val passwordHash =
            PasswordHasher.hash(password)

        val role = roleService.getRoleByCode(roleCode)
            ?: throw IllegalArgumentException(
                "Роль '$roleCode' не існує."
            )

        return userRepository.create(
            username = username,
            email = email,
            password = passwordHash,
            roleId = role.id
        )
    }

    /**
     * Перевіряє коректність даних нового користувача.
     *
     * Якщо будь-яка перевірка не проходить,
     * генерується виняток IllegalArgumentException.
     */
    private fun validateUserData(
        username: String,
        email: String,
        password: String
    ) {

        if (username.isBlank()) {
            throw IllegalArgumentException(
                "Логін користувача не може бути порожнім."
            )
        }

        if (username.length < 3) {
            throw IllegalArgumentException(
                "Логін повинен містити щонайменше 3 символи."
            )
        }

        if (email.isBlank()) {
            throw IllegalArgumentException(
                "Email не може бути порожнім."
            )
        }

        if ('@' !in email) {
            throw IllegalArgumentException(
                "Некоректний формат email."
            )
        }

        if (password.length < 5) {
            throw IllegalArgumentException(
                "Пароль повинен містити щонайменше 5 символів."
            )
        }
    }
}