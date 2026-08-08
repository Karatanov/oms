package oms.ufsi.service

import oms.ufsi.domain.User
import oms.ufsi.security.PasswordHasher

/**
 * Сервіс автентифікації користувачів.
 *
 * Його відповідальність:
 * - пошук користувача;
 * - перевірка пароля;
 * - повернення автентифікованого користувача.
 *
 * Видача JWT-токенів буде додана пізніше.
 */
class AuthService(
    private val userService: UserService
) {

    /**
     * Виконує автентифікацію користувача.
     *
     * Якщо логін або пароль неправильні,
     * генерується IllegalArgumentException.
     */
    fun login(
        username: String,
        password: String
    ): User {

        val user = userService.findByLoginOrEmail(username)
            ?: throw IllegalArgumentException(
                "Невірний логін або пароль."
            )

        val passwordIsValid =
            PasswordHasher.verify(
                password,
                user.passwordHash
            )

        if (!passwordIsValid) {
            throw IllegalArgumentException(
                "Невірний логін або пароль."
            )
        }

        return user
    }
}
