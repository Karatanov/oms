package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object ProjectDocumentTable : LongIdTable("documents") {
    val uuid = varchar("uuid", 36).uniqueIndex(); val projectId = reference("project_id", ProjectTable, onDelete = ReferenceOption.CASCADE); val docType = varchar("doc_type", 20); val originalName = varchar("original_name", 255); val storagePath = varchar("storage_path", 500); val contentType = varchar("content_type", 100); val fileSizeBytes = long("file_size_bytes"); val createdBy = reference("created_by", UserTable, onDelete = ReferenceOption.RESTRICT)
}
