package oms.ufsi.repository

import oms.ufsi.database.tables.RoleTable
import oms.ufsi.database.tables.UserTable
import oms.ufsi.domain.Role
import oms.ufsi.domain.User
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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

                    passwordHash = row[UserTable.password],

                    role = Role(
                        id = row[RoleTable.id].value,
                        code = row[RoleTable.code],
                        name = row[RoleTable.name]
                    )
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

                    passwordHash = row[UserTable.password],

                    role = Role(
                        id = row[RoleTable.id].value,
                        code = row[RoleTable.code],
                        name = row[RoleTable.name]
                    )
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
        roleId: Long
    ): User = transaction {

        val userId = UserTable.insertAndGetId {

            it[UserTable.username] = username
            it[UserTable.email] = email
            it[UserTable.password] = password
            it[UserTable.roleId] = roleId
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
}