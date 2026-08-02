package oms.ufsi.domain

data class InspectionReportFile(
    val inspectionReportId: Long,
    val originalName: String,
    val storagePath: String,
    val contentType: String,
    val fileSizeBytes: Long
)
