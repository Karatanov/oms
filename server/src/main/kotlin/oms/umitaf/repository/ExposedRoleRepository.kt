package oms.umitaf.repository

import oms.umitaf.database.tables.RoleTable
import oms.umitaf.domain.Role
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Реалізація репозиторію на базі Exposed.
 */
class ExposedRoleRepository : RoleRepository {
    /**
     * Виконує пошук ролі за її системним кодом.
     */
    override fun findByCode(code: String): Role? = transaction {

        RoleTable
            .selectAll()
            .where { RoleTable.code eq code }
            .singleOrNull()
            ?.let { row ->

                Role(
                    id = row[RoleTable.id].value,
                    code = row[RoleTable.code],
                    name = row[RoleTable.name]
                )
            }
    }

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