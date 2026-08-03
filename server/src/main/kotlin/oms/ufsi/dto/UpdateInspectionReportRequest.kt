package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInspectionReportRequest(
    val inspectionDate: String,
    val summary: String? = null
)
