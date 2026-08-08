package oms.ufsi.service

import oms.ufsi.domain.User
import oms.ufsi.repository.UserRepository
import oms.ufsi.security.PasswordHasher
import oms.ufsi.dto.UpdateUserRequest

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
     * Повертає користувача за логіном.
     */
    fun findByUsername(
        username: String
    ): User? {

        return userRepository.findByUsername(
            username.trim()
        )
    }

    fun findByLoginOrEmail(
        loginOrEmail: String
    ): User? {
        val normalizedValue = loginOrEmail.trim()

        return findByUsername(normalizedValue)
            ?: getAllUsers().firstOrNull { user ->
                user.email.equals(normalizedValue, ignoreCase = true)
            }
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
         * Логін користувача повинен бути унікальним.
         */
        if (userRepository.existsByUsername(username)) {

            throw IllegalArgumentException(
                "Користувач з таким логіном вже існує."
            )
        }

        /**
         * Email також повинен бути унікальним.
         */
        if (userRepository.existsByEmail(email)) {

            throw IllegalArgumentException(
                "Користувач з таким email вже існує."
            )
        }
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

    fun updateUser(id: Long, request: UpdateUserRequest): User? {
        val current = getAllUsers().firstOrNull { it.id == id } ?: return null
        val username = request.username?.trim() ?: current.username
        val email = request.email?.trim() ?: current.email
        val password = request.password?.trim()
        validateUserData(username, email, password ?: "valid-existing-password")
        if (username != current.username && userRepository.existsByUsername(username)) {
            throw IllegalArgumentException("A user with this username already exists.")
        }
        if (!email.equals(current.email, ignoreCase = true) && userRepository.existsByEmail(email)) {
            throw IllegalArgumentException("A user with this email already exists.")
        }
        val role = request.roleCode?.trim()?.takeIf { it.isNotEmpty() }?.let { roleService.getRoleByCode(it) }
            ?: if (request.roleCode.isNullOrBlank()) current.role else throw IllegalArgumentException("Role does not exist.")
        val passwordHash = if (password.isNullOrBlank()) current.passwordHash else PasswordHasher.hash(password)
        return userRepository.update(id, username, email, passwordHash, role.id)
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

        if (password.length < 8 || password.none { it.isLetter() } || password.none { it.isDigit() }) {
            throw IllegalArgumentException(
                "Пароль повинен містити щонайменше 5 символів."
            )
        }
    }


}
