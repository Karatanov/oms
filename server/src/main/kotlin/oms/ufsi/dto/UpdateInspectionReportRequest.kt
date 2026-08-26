package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInspectionReportRequest(
    val inspectionDate: String,
    val summary: String? = null,
    val reportCode: String? = null,
    val inspectionType: String = "planned",
    val latitude: Double? = null,
    val longitude: Double? = null
)
