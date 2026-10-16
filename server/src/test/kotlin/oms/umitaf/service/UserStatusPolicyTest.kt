package oms.umitaf.service

import oms.umitaf.domain.Role
import oms.umitaf.domain.User
import oms.umitaf.repository.RoleRepository
import oms.umitaf.repository.UserRepository
import oms.umitaf.security.PasswordHasher
import oms.umitaf.dto.UpdateUserRequest
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserStatusPolicyTest {
    private val adminRole = Role(1, "ADMIN", "Administrator")

    @Test fun `new users are active by default and an administrator can toggle active disabled`() {
        val repository = InMemoryUsers(adminRole)
        val service = UserService(repository, RoleService(SingleRoleRepository(adminRole)))

        val created = service.createUser("new.user", "new@example.test", "Password1", "ADMIN")
        assertEquals("active", created.status)
        assertEquals("disabled", service.updateUser(created.id, UpdateUserRequest(status = "disabled"))?.status)
        assertEquals("active", service.updateUser(created.id, UpdateUserRequest(status = "active"))?.status)
        assertFailsWith<IllegalArgumentException> {
            service.createUser("legacy.user", "legacy@example.test", "Password1", "ADMIN", status = "pending")
        }
    }

    @Test fun `disabled users cannot authenticate`() {
        val repository = InMemoryUsers(adminRole).apply {
            users += User(1, "disabled.user", "disabled@example.test", PasswordHasher.hash("Password1"), adminRole, status = "disabled")
        }
        val userService = UserService(repository, RoleService(SingleRoleRepository(adminRole)))

        assertFailsWith<IllegalArgumentException> {
            AuthService(userService, repository).login("disabled.user", "Password1")
        }
    }

    private class SingleRoleRepository(private val role: Role) : RoleRepository {
        override fun findAll() = listOf(role)
        override fun findByCode(code: String) = role.takeIf { it.code.equals(code, ignoreCase = true) }
    }

    private class InMemoryUsers(private val role: Role) : UserRepository {
        val users = mutableListOf<User>()

        override fun findAll() = users.toList()
        override fun create(username: String, email: String, password: String, roleId: Long, firstName: String, lastName: String, status: String, region: String?, department: String?, preferredLang: String): User {
            val user = User(users.size.toLong() + 1, username, email, password, role, firstName, lastName, status, region, department, preferredLang)
            users += user
            return user
        }
        override fun findByUsername(username: String) = users.firstOrNull { it.username == username }
        override fun existsByUsername(username: String) = users.any { it.username == username }
        override fun existsByEmail(email: String) = users.any { it.email.equals(email, ignoreCase = true) }
        override fun update(id: Long, username: String, email: String, passwordHash: String, roleId: Long, firstName: String, lastName: String, status: String, region: String?, department: String?, preferredLang: String): User? {
            val index = users.indexOfFirst { it.id == id }
            if (index < 0) return null
            return users[index].copy(username = username, email = email, passwordHash = passwordHash, firstName = firstName, lastName = lastName, status = status, region = region, department = department, preferredLang = preferredLang).also { users[index] = it }
        }
        override fun delete(id: Long) = users.removeIf { it.id == id }
        override fun archive(id: Long, archivedBy: Long) = false
        override fun restore(id: Long) = false
        override fun dependencyCounts(id: Long) = emptyMap<String, Long>()
        override fun authenticationState(userId: Long) = users.firstOrNull { it.id == userId }?.let { UserRepository.AuthenticationState(it.status, it.failedLoginCount, it.lockedUntil) }
        override fun recordFailedLogin(userId: Long, lockedUntil: LocalDateTime?) = Unit
        override fun recordSuccessfulLogin(userId: Long, at: LocalDateTime) = Unit
        override fun storePasswordResetToken(userId: Long, tokenHash: String, expiresAt: LocalDateTime) = Unit
        override fun activationState(tokenHash: String): UserRepository.ActivationState? = null
        override fun resetPassword(userId: Long, passwordHash: String, at: LocalDateTime) = users.indexOfFirst { it.id == userId } >= 0
    }
}
