package oms.umitaf.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object InspectionReportFileTable : LongIdTable("inspection_report_files") {
    val inspectionReportId = reference("inspection_report_id", InspectionReportTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val originalName = varchar("original_name", 255)
    val storagePath = varchar("storage_path", 500)
    val contentType = varchar("content_type", 100)
    val fileSizeBytes = long("file_size_bytes")
}
