package oms.umitaf.service

import oms.umitaf.domain.User
import oms.umitaf.repository.UserRepository
import oms.umitaf.security.PasswordHasher
import oms.umitaf.dto.UpdateUserRequest

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
        roleCode: String,
        firstName: String = "",
        lastName: String = "",
        status: String = "active",
        region: String? = null,
        department: String? = null,
        preferredLang: String = "uk"
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
            roleId = role.id,
            firstName = firstName.trim(), lastName = lastName.trim(),
            status = normalizeStatus(status), region = region?.trim()?.ifBlank { null },
            department = department?.trim()?.ifBlank { null }, preferredLang = normalizeLanguage(preferredLang)
        )
    }

    fun updateUser(id: Long, request: UpdateUserRequest): User? {
        val current = getAllUsers().firstOrNull { it.id == id } ?: return null
        val username = request.username?.trim() ?: current.username
        val email = request.email?.trim() ?: current.email
        val password = request.password?.trim()?.takeIf { it.isNotBlank() }
        validateUserData(username, email, password)
        if (username != current.username && userRepository.existsByUsername(username)) {
            throw IllegalArgumentException("A user with this username already exists.")
        }
        if (!email.equals(current.email, ignoreCase = true) && userRepository.existsByEmail(email)) {
            throw IllegalArgumentException("A user with this email already exists.")
        }
        val role = request.roleCode?.trim()?.takeIf { it.isNotEmpty() }?.let { roleService.getRoleByCode(it) }
            ?: if (request.roleCode.isNullOrBlank()) current.role else throw IllegalArgumentException("Role does not exist.")
        val passwordHash = if (password.isNullOrBlank()) current.passwordHash else PasswordHasher.hash(password)
        return userRepository.update(
            id, username, email, passwordHash, role.id,
            request.firstName?.trim() ?: current.firstName,
            request.lastName?.trim() ?: current.lastName,
            request.status?.let(::normalizeStatus) ?: current.status,
            if (request.region == null) current.region else request.region.trim().ifBlank { null },
            if (request.department == null) current.department else request.department.trim().ifBlank { null },
            request.preferredLang?.let(::normalizeLanguage) ?: current.preferredLang
        )
    }

    fun deleteUser(id: Long): Boolean = userRepository.delete(id)

    private fun normalizeStatus(value: String): String {
        val status = value.trim().lowercase()
        require(status in setOf("active", "pending", "disabled")) { "User status must be active, pending, or disabled." }
        return status
    }

    private fun normalizeLanguage(value: String): String {
        val language = value.trim().lowercase()
        require(language in setOf("uk", "en")) { "Preferred language must be uk or en." }
        return language
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
        password: String?
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

        if (password != null && (password.length < 8 || password.none { it.isLetter() } || password.none { it.isDigit() })) {
            throw IllegalArgumentException(
                "Пароль повинен містити щонайменше 8 символів, літеру та цифру."
            )
        }
    }


}
