package oms.umitaf.dto

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

/** Explicit operational status correction, restricted to Admin and Project Manager. */
@Serializable
data class UpdateInspectionReportStatusRequest(
    val status: String
)
