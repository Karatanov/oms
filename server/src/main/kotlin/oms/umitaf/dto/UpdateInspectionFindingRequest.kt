package oms.umitaf.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInspectionFindingRequest(
    val category: String,
    val severity: String,
    val description: String,
    val recommendation: String? = null,
    val isResolved: Boolean
)
