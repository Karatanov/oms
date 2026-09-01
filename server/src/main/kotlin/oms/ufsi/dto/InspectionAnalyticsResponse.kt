package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class InspectionAnalyticsResponse(
    val monthlyInspectionCounts: List<DashboardMetricResponse> = emptyList(),
    val monthlyEshsViolations: List<DashboardMetricResponse> = emptyList()
)
