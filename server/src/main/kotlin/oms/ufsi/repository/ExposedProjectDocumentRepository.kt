package oms.ufsi.repository

import oms.ufsi.database.tables.ProjectDocumentTable
import oms.ufsi.domain.ProjectDocument
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class ExposedProjectDocumentRepository : ProjectDocumentRepository {
    override fun findByProjectId(projectId: Long) = transaction { ProjectDocumentTable.selectAll().filter { it[ProjectDocumentTable.projectId].value == projectId }.map(::map) }
    override fun findByUuid(projectId: Long, uuid: String) = transaction { ProjectDocumentTable.selectAll().firstOrNull { it[ProjectDocumentTable.projectId].value == projectId && it[ProjectDocumentTable.uuid] == uuid }?.let(::map) }
    override fun create(document: ProjectDocument) { transaction { ProjectDocumentTable.insertAndGetId { it[uuid] = document.uuid.toString(); it[projectId] = document.projectId; it[docType] = document.docType; it[originalName] = document.originalName; it[storagePath] = document.storagePath; it[contentType] = document.contentType; it[fileSizeBytes] = document.fileSizeBytes; it[createdBy] = 1L } } }
    private fun map(r: org.jetbrains.exposed.v1.core.ResultRow) = ProjectDocument(r[ProjectDocumentTable.id].value, UUID.fromString(r[ProjectDocumentTable.uuid]), r[ProjectDocumentTable.projectId].value, r[ProjectDocumentTable.docType], r[ProjectDocumentTable.originalName], r[ProjectDocumentTable.storagePath], r[ProjectDocumentTable.contentType], r[ProjectDocumentTable.fileSizeBytes])
}
