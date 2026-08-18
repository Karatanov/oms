package oms.ufsi.repository

import oms.ufsi.database.tables.RoleTable
import oms.ufsi.database.tables.UserTable
import oms.ufsi.domain.Role
import oms.ufsi.domain.User
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.time.LocalDateTime
import java.util.UUID

/**
 * Реалізація репозиторію користувачів на базі Exposed.
 */
class ExposedUserRepository : UserRepository {

    override fun findAll(): List<User> = transaction {

        /**
         * Виконуємо INNER JOIN між таблицями
         * користувачів та ролей.
         */
        UserTable
            .innerJoin(RoleTable)
            .selectAll()
            .map { row ->

                User(
                    id = row[UserTable.id].value,

                    username = row[UserTable.username],

                    email = row[UserTable.email],

                    passwordHash = row[UserTable.passwordHash],

                    role = Role(
                        id = row[RoleTable.id].value,
                        code = row[RoleTable.code],
                        name = row[RoleTable.name]
                    ),
                    firstName = row[UserTable.firstName],
                    lastName = row[UserTable.lastName],
                    status = row[UserTable.status],
                    region = row[UserTable.region],
                    department = row[UserTable.department],
                    preferredLang = row[UserTable.preferredLang],
                    lastLoginAt = row[UserTable.lastLoginAt],
                    failedLoginCount = row[UserTable.failedLoginCount],
                    lockedUntil = row[UserTable.lockedUntil],
                    createdAt = row[UserTable.createdAt],
                    updatedAt = row[UserTable.updatedAt]
                )
            }
    }

    /**
     * Виконує пошук користувача за логіном.
     */
    override fun findByUsername(
        username: String
    ): User? = transaction {

        UserTable
            .innerJoin(RoleTable)
            .selectAll()
            .firstOrNull { row ->

                row[UserTable.username] == username
            }
            ?.let { row ->

                User(
                    id = row[UserTable.id].value,

                    username = row[UserTable.username],

                    email = row[UserTable.email],

                    passwordHash = row[UserTable.passwordHash],

                    role = Role(
                        id = row[RoleTable.id].value,
                        code = row[RoleTable.code],
                        name = row[RoleTable.name]
                    ),
                    firstName = row[UserTable.firstName],
                    lastName = row[UserTable.lastName],
                    status = row[UserTable.status],
                    region = row[UserTable.region],
                    department = row[UserTable.department],
                    preferredLang = row[UserTable.preferredLang],
                    lastLoginAt = row[UserTable.lastLoginAt],
                    failedLoginCount = row[UserTable.failedLoginCount],
                    lockedUntil = row[UserTable.lockedUntil],
                    createdAt = row[UserTable.createdAt],
                    updatedAt = row[UserTable.updatedAt]
                )
            }
    }

    /**
     * Створює нового користувача.
     */
    override fun create(
        username: String,
        email: String,
        password: String,
        roleId: Long,
        firstName: String,
        lastName: String,
        status: String,
        region: String?,
        department: String?,
        preferredLang: String
    ): User = transaction {

        val userId = UserTable.insertAndGetId {

            it[UserTable.uuid] = UUID.randomUUID().toString()
            it[UserTable.username] = username
            it[UserTable.email] = email
            it[UserTable.passwordHash] = password
            it[UserTable.roleId] = roleId
            it[UserTable.firstName] = firstName
            it[UserTable.lastName] = lastName
            it[UserTable.status] = status
            it[UserTable.region] = region
            it[UserTable.department] = department
            it[UserTable.preferredLang] = preferredLang
        }

        val role = RoleTable
            .selectAll()
            .first { row ->

                row[RoleTable.id].value == roleId
            }

        User(
            id = userId.value,

            username = username,

            email = email,

            passwordHash = password,

            role = Role(
                id = role[RoleTable.id].value,
                code = role[RoleTable.code],
                name = role[RoleTable.name]
            )
        )

    }

    /**
     * Перевіряє наявність користувача
     * з указаним логіном.
     */
    override fun existsByUsername(
        username: String
    ): Boolean = transaction {

        UserTable
            .selectAll()
            .any { row ->

                row[UserTable.username] == username
            }
    }

    /**
     * Перевіряє наявність користувача
     * з указаною електронною поштою.
     */
    override fun existsByEmail(
        email: String
    ): Boolean = transaction {

        UserTable
            .selectAll()
            .any { row ->

                row[UserTable.email] == email
            }
    }

    override fun update(id: Long, username: String, email: String, passwordHash: String, roleId: Long, firstName: String, lastName: String, status: String, region: String?, department: String?, preferredLang: String): User? {
        val count = transaction {
            UserTable.update({ UserTable.id eq id }) {
            it[UserTable.username] = username
            it[UserTable.email] = email
            it[UserTable.passwordHash] = passwordHash
            it[UserTable.roleId] = roleId
            it[UserTable.firstName] = firstName
            it[UserTable.lastName] = lastName
            it[UserTable.status] = status
            it[UserTable.region] = region
            it[UserTable.department] = department
            it[UserTable.preferredLang] = preferredLang
            it[UserTable.updatedAt] = LocalDateTime.now()
            }
        }
        return if (count == 0) null else findAll().firstOrNull { it.id == id }
    }

    override fun delete(id: Long): Boolean = transaction { UserTable.deleteWhere { UserTable.id eq id } > 0 }

    override fun authenticationState(userId: Long): UserRepository.AuthenticationState? = transaction {
        UserTable.selectAll().firstOrNull { it[UserTable.id].value == userId }?.let { row ->
            UserRepository.AuthenticationState(
                status = row[UserTable.status],
                failedLoginCount = row[UserTable.failedLoginCount],
                lockedUntil = row[UserTable.lockedUntil]
            )
        }
    }

    override fun recordFailedLogin(userId: Long, lockedUntil: LocalDateTime?) = transaction {
        val current = UserTable.selectAll().firstOrNull { it[UserTable.id].value == userId } ?: return@transaction
        UserTable.update({ UserTable.id eq userId }) {
            it[failedLoginCount] = current[UserTable.failedLoginCount] + 1
            it[UserTable.lockedUntil] = lockedUntil
        }
    }

    override fun recordSuccessfulLogin(userId: Long, at: LocalDateTime) = transaction {
        UserTable.update({ UserTable.id eq userId }) {
            it[lastLoginAt] = at
            it[failedLoginCount] = 0
            it[lockedUntil] = null
        }
        Unit
    }
}
