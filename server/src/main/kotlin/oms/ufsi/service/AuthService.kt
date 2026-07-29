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

        println("AUTH DEBUG: username from request = '$username'")

        val user = userService.findByUsername(username)
            ?: throw IllegalArgumentException(
                "Невірний логін або пароль."
            )

        println("AUTH DEBUG: user found = ${user.username}")
        println("AUTH DEBUG: password hash length = ${user.passwordHash.length}")

        val passwordIsValid =
            PasswordHasher.verify(
                password,
                user.passwordHash
            )

        println("AUTH DEBUG: password is valid = $passwordIsValid")

        if (!passwordIsValid) {
            throw IllegalArgumentException(
                "Невірний логін або пароль."
            )
        }

        return user
    }
}