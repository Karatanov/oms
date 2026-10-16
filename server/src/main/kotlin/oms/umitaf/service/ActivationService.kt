package oms.umitaf.service

import oms.umitaf.repository.UserRepository
import oms.umitaf.security.PasswordHasher
import oms.umitaf.storage.uploadDirectory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

/** Issues one-time password-reset links and writes development mail to a local outbox.
 * SMTP can replace the outbox without changing the token lifecycle or API.
 */
class ActivationService(private val userRepository: UserRepository) {
    fun issuePasswordReset(userId: Long, email: String) {
        issueLink(userId, email, "password reset") { token, expiresAt ->
            userRepository.storePasswordResetToken(userId, hash(token), expiresAt)
        }
    }

    fun resetPassword(token: String, password: String): Long {
        require(password.length >= 8 && password.any(Char::isLetter) && password.any(Char::isDigit)) { "Password must contain at least 8 characters, including letters and numbers." }
        val state = userRepository.activationState(hash(token.trim())) ?: throw IllegalArgumentException("Activation link is invalid.")
        require(state.expiresAt?.isAfter(LocalDateTime.now()) == true) { "Activation link has expired." }
        // Resetting a password must not silently re-enable an administrator-disabled account.
        require(userRepository.resetPassword(state.userId, PasswordHasher.hash(password), LocalDateTime.now())) { "Password reset failed." }
        return state.userId
    }

    private fun issueLink(userId: Long, email: String, subject: String, store: (String, LocalDateTime) -> Unit) {
        val token = ByteArray(32).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        store(token, LocalDateTime.now().plusHours(24))
        val baseUrl = System.getenv("OMS_PUBLIC_BASE_URL")?.trim()?.trimEnd('/')?.ifBlank { null } ?: "http://localhost:8080"
        val outbox = uploadDirectory("activation-outbox")
        Files.createDirectories(outbox)
        val body = "To: $email\nSubject: OMS $subject\n\nSet a new password within 24 hours:\n$baseUrl/?token=$token\n"
        Files.writeString(outbox.resolve("${subject.replace(' ', '-') }-$userId.eml"), body, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
