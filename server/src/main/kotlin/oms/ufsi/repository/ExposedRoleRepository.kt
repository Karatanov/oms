package oms.ufsi.repository

import oms.ufsi.database.tables.RoleTable
import oms.ufsi.domain.Role
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Реалізація репозиторію на базі Exposed.
 */
class ExposedRoleRepository : RoleRepository {

    override fun findAll(): List<Role> = transaction {

        RoleTable
            .selectAll()
            .map { row ->

                /**
                 * Перетворюємо рядок таблиці
                 * на доменний об'єкт.
                 */
                Role(
                    id = row[RoleTable.id].value,
                    code = row[RoleTable.code],
                    name = row[RoleTable.name]
                )
            }
    }
}