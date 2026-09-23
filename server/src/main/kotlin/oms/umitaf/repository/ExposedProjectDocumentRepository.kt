package oms.umitaf.repository

import oms.umitaf.database.tables.ProjectDocumentTable
import oms.umitaf.domain.ProjectDocument
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class ExposedProjectDocumentRepository : ProjectDocumentRepository {
    override fun findAll() = transaction { ProjectDocumentTable.selectAll().map(::map) }
    override fun findByProjectId(projectId: Long) = transaction { ProjectDocumentTable.selectAll().where { ProjectDocumentTable.projectId eq projectId }.map(::map) }
    override fun findByUuid(projectId: Long, uuid: String) = transaction { ProjectDocumentTable.selectAll().where { (ProjectDocumentTable.projectId eq projectId) and (ProjectDocumentTable.uuid eq uuid) }.limit(1).firstOrNull()?.let(::map) }
    override fun create(document: ProjectDocument) { transaction { ProjectDocumentTable.insertAndGetId { it[uuid] = document.uuid.toString(); it[projectId] = document.projectId; it[relatedEntity] = document.relatedEntity; it[relatedId] = document.relatedId; it[docType] = document.docType; it[description] = document.description; it[originalName] = document.originalName; it[storagePath] = document.storagePath; it[contentType] = document.contentType; it[fileSizeBytes] = document.fileSizeBytes; it[createdBy] = 1L } } }
    override fun delete(projectId: Long, uuid: String) = transaction { ProjectDocumentTable.deleteWhere { (ProjectDocumentTable.projectId eq projectId) and (ProjectDocumentTable.uuid eq uuid) } > 0 }
    private fun map(r: org.jetbrains.exposed.v1.core.ResultRow) = ProjectDocument(r[ProjectDocumentTable.id].value, UUID.fromString(r[ProjectDocumentTable.uuid]), r[ProjectDocumentTable.projectId].value, r[ProjectDocumentTable.relatedEntity], r[ProjectDocumentTable.relatedId], r[ProjectDocumentTable.docType], r[ProjectDocumentTable.description], r[ProjectDocumentTable.originalName], r[ProjectDocumentTable.storagePath], r[ProjectDocumentTable.contentType], r[ProjectDocumentTable.fileSizeBytes])
}
