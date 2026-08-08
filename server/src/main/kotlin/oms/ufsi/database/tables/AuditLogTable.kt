package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.datetime

/** Immutable audit trail table defined in Section 2.2.9. */
object AuditLogTable : LongIdTable("audit_log") {
    val userId = optReference("user_id", UserTable, onDelete = ReferenceOption.RESTRICT)
    val action = varchar("action", 100)
    val entityType = varchar("entity_type", 50)
    val entityId = long("entity_id")
    val oldValues = text("old_values").nullable()
    val newValues = text("new_values").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = datetime("created_at")
}
