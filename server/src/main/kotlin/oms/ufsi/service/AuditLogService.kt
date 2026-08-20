package oms.ufsi.service

import oms.ufsi.database.tables.AuditLogTable
import oms.ufsi.database.tables.UserTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime

data class ActivityEntry(val action: String, val entityType: String, val entityId: Long, val userLogin: String?, val createdAt: LocalDateTime)

class AuditLogService {
    fun record(
        userId: Long?,
        action: String,
        entityType: String,
        entityId: Long,
        oldValues: String? = null,
        newValues: String? = null
    ) = transaction {
        AuditLogTable.insert {
            it[AuditLogTable.userId] = userId
            it[AuditLogTable.action] = action
            it[AuditLogTable.entityType] = entityType
            it[AuditLogTable.entityId] = entityId
            it[AuditLogTable.oldValues] = oldValues
            it[AuditLogTable.newValues] = newValues
            it[AuditLogTable.createdAt] = LocalDateTime.now()
        }
    }

    fun recent(limit: Int = 8): List<ActivityEntry> = transaction {
        val rows = AuditLogTable.selectAll()
            .orderBy(AuditLogTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .toList()
        val usernamesById = UserTable.selectAll().associate { it[UserTable.id].value to it[UserTable.username] }
        rows.map {
            ActivityEntry(
                it[AuditLogTable.action], it[AuditLogTable.entityType], it[AuditLogTable.entityId],
                it[AuditLogTable.userId]?.value?.let(usernamesById::get), it[AuditLogTable.createdAt]
            )
        }
    }
}
