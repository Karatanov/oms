package oms.umitaf.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.datetime

/** HSE incident register defined in Section 2.2.8. */
object IncidentTable : LongIdTable("incidents") {
    val projectId = reference("project_id", ProjectTable, onDelete = ReferenceOption.RESTRICT)
    val reportedBy = reference("reported_by", UserTable, onDelete = ReferenceOption.RESTRICT)
    val severity = varchar("severity", 20)
    val description = text("description")
    val incidentDate = datetime("incident_date")
    val latitude = decimal("latitude", 10, 7).nullable()
    val longitude = decimal("longitude", 10, 7).nullable()
    val status = varchar("status", 20)
    val assignedTo = optReference("assigned_to", UserTable, onDelete = ReferenceOption.RESTRICT)
    val resolutionNotes = text("resolution_notes").nullable()
    val resolvedAt = datetime("resolved_at").nullable()
}
