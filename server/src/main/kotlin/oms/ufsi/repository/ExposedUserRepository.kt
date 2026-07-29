package oms.ufsi.repository

import oms.ufsi.database.tables.RoleTable
import oms.ufsi.database.tables.UserTable
import oms.ufsi.domain.Role
import oms.ufsi.domain.User
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
}