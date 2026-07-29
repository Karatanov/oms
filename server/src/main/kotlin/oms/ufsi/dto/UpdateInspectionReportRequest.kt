package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInspectionReportRequest(
    val inspectionDate: String,
    val completionPct: Double,
    val summary: String? = null
)
