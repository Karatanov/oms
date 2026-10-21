package oms.urp3.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReviewInspectionReportRequest(
    val action: String,
    val rejectionReason: String? = null
)
