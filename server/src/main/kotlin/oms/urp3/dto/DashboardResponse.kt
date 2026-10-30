package oms.urp3.dto

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
data class DashboardOverviewResponse(
    val recentInspections: List<InspectionReportResponse> = emptyList(),
    val monthlyActPayments: List<MonthlyActPaymentResponse> = emptyList(),
    val subprojectFunding: List<SubprojectFundingResponse> = emptyList(),
    val subprojectPaymentProgress: List<SubprojectPaymentProgressResponse> = emptyList(),
    val procurementStatusCounts: List<DashboardMetricResponse> = emptyList(),
    val signedContractsByType: List<DashboardContractTypeMetricResponse> = emptyList(),
    val nationalSummary: DashboardNationalSummaryResponse? = null
)

@Serializable
data class ActivityResponse(val action: String, val entityType: String, val entityId: Long, val userLogin: String? = null, val createdAt: String)

@Serializable
data class MonthlyActPaymentResponse(
    val month: String,
    val amountEurCents: Long,
    val amountUahCents: Long? = null
)
@Serializable data class SubprojectFundingResponse(
    val projectUuid: String,
    val name: String,
    val region: String,
    val amountUah: Double,
    val amountEur: Double
)
@Serializable data class SubprojectProgressResponse(
    val projectUuid: String,
    val code: String,
    val name: String,
    val nameEn: String? = null,
    val region: String,
    val completionPct: Double
)
@Serializable data class SubprojectPaymentProgressResponse(
    val projectUuid: String,
    val code: String,
    val region: String,
    val eligibleContractValueUah: Double? = null,
    val paidAmountUah: Double? = null,
    val paymentPct: Double? = null
)
@Serializable data class DashboardMetricResponse(val label: String, val value: Long)
@Serializable data class DashboardContractTypeMetricResponse(
    val type: String,
    val contractsCount: Long,
    val contractAmountUah: Double
)
@Serializable data class DashboardNationalSummaryResponse(
    val subprojectsCount: Long,
    val regionsCount: Long,
    val municipalitiesCount: Long,
    val settlementsCount: Long,
    val approvedFundingUah: Double,
    val approvedFundingEur: Double,
    val signedContractsCount: Long,
    val signedContractsAmountUah: Double,
    val servicesPaidEurCents: Long,
    val servicesPaidUahCents: Long? = null,
    val eibPaidUnderContractsUah: Double? = null
)
