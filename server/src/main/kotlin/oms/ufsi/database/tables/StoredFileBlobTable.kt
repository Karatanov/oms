package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/** Durable fallback for uploads when the deployment filesystem is ephemeral. */
object StoredFileBlobTable : Table("stored_file_blobs") {
    val storagePath = varchar("storage_path", 500)
    val content = blob("content")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(storagePath)
}
