package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class DashboardResponse(
    val projectsTotal: Long,
    val projectsActive: Long,
    val projectsCompletedThisMonth: Long,
    val budgetPlanned: Long,
    val amountSpent: Long,
    val inspectionsTotal: Long,
    val pendingInspections: Long,
    val findingsTotal: Long,
    val recentInspections: List<InspectionReportResponse>,
    val activities: List<ActivityResponse>
)

@Serializable
data class ActivityResponse(val action: String, val entityType: String, val entityId: Long, val userLogin: String? = null, val createdAt: String)
