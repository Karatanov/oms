package oms.ufsi.database.tables
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
object InspectionPhotoTable : LongIdTable("inspection_photos") { val uuid=varchar("uuid",36).uniqueIndex(); val reportId=reference("inspection_report_id",InspectionReportTable,onDelete=ReferenceOption.CASCADE); val originalName=varchar("original_name",255); val storagePath=varchar("storage_path",500); val thumbnailPath=varchar("thumbnail_path",500); val contentType=varchar("content_type",100); val fileSizeBytes=long("file_size_bytes"); val isMain=bool("is_main") }
