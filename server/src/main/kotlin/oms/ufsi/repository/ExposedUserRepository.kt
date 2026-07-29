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

            role = Role(
                id = role[RoleTable.id].value,
                code = role[RoleTable.code],
                name = role[RoleTable.name]
            )
        )
    }
}