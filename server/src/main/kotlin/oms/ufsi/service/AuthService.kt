package oms.ufsi.service

import oms.ufsi.domain.User
import oms.ufsi.repository.UserRepository
import oms.ufsi.security.PasswordHasher
import java.time.Clock
import java.time.LocalDateTime

class AccountLockedException(val lockedUntil: LocalDateTime) : IllegalArgumentException("Account is temporarily locked.")

/**
 * Сервіс автентифікації користувачів.
 *
 * Його відповідальність:
 * - пошук користувача;
 * - перевірка пароля;
 * - повернення автентифікованого користувача.
 *
 * The route layer issues the short-lived JWT after this service has verified
 * credentials; this service remains responsible only for authentication state.
 */
class AuthService(
    private val userService: UserService,
    private val userRepository: UserRepository
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

        val state = userRepository.authenticationState(user.id)
            ?: throw IllegalArgumentException("Invalid username or password.")
        // Authentication timestamps are persisted as UTC.  Render runs in UTC while
        // local development may not, so relying on the host default timezone makes
        // the same account appear to move backwards or forwards between deployments.
        val now = LocalDateTime.now(Clock.systemUTC())
        if (!state.status.equals("active", ignoreCase = true)) {
            throw IllegalArgumentException("This account is not active.")
        }
        if (state.lockedUntil?.isAfter(now) == true) {
            throw AccountLockedException(state.lockedUntil)
        }

        val passwordIsValid =
            PasswordHasher.verify(
                password,
                user.passwordHash
            )

        if (!passwordIsValid) {
            val nextAttempts = state.failedLoginCount + 1
            userRepository.recordFailedLogin(
                user.id,
                if (nextAttempts >= 5) now.plusMinutes(15) else state.lockedUntil
            )
            throw IllegalArgumentException(
                "Невірний логін або пароль."
            )
        }

        userRepository.recordSuccessfulLogin(user.id, now)

        // Return the freshly persisted authentication state.  The previous response
        // contained the User instance read before recordSuccessfulLogin(), leaving
        // lastLoginAt stale until a later users-list refresh.
        return userService.findByLoginOrEmail(username)
            ?: user.copy(lastLoginAt = now, failedLoginCount = 0, lockedUntil = null)
    }
}
