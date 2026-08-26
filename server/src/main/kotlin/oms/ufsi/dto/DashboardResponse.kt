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
    val activities: List<ActivityResponse>,
    val monthlyActPayments: List<MonthlyActPaymentResponse> = emptyList(),
    val subprojectFunding: List<SubprojectFundingResponse> = emptyList(),
    val subprojectProgress: List<SubprojectProgressResponse> = emptyList(),
    val procurementStatusCounts: List<DashboardMetricResponse> = emptyList(),
    val monthlyInspectionCounts: List<DashboardMetricResponse> = emptyList(),
    val monthlyEshsViolations: List<DashboardMetricResponse> = emptyList(),
    val monthlyEquipmentPayments: List<MonthlyActPaymentResponse> = emptyList(),
    val monthlySignedConstructionContracts: List<DashboardMetricResponse> = emptyList()
)

@Serializable
data class ActivityResponse(val action: String, val entityType: String, val entityId: Long, val userLogin: String? = null, val createdAt: String)

@Serializable
data class MonthlyActPaymentResponse(val month: String, val amountEurCents: Long)
@Serializable data class SubprojectFundingResponse(val projectUuid: String, val name: String, val region: String, val amount: Long)
@Serializable data class SubprojectProgressResponse(val projectUuid: String, val name: String, val completionPct: Double)
@Serializable data class DashboardMetricResponse(val label: String, val value: Long)
