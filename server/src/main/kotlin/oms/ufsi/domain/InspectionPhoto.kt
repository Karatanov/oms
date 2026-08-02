package oms.ufsi.domain
import java.util.UUID
data class InspectionPhoto(val id: Long, val uuid: UUID, val reportId: Long, val originalName: String, val storagePath: String, val thumbnailPath: String, val contentType: String, val fileSizeBytes: Long, val isMain: Boolean)
