package oms.urp3.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import oms.urp3.config.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oms.urp3.dto.DashboardResponse
import oms.urp3.dto.DashboardOverviewResponse
import oms.urp3.dto.ActivityResponse
import oms.urp3.dto.toResponse
import oms.urp3.dto.MonthlyActPaymentResponse
import oms.urp3.dto.SubprojectFundingResponse
import oms.urp3.dto.SubprojectProgressResponse
import oms.urp3.dto.SubprojectPaymentProgressResponse
import oms.urp3.dto.DashboardMetricResponse
import oms.urp3.dto.DashboardContractTypeMetricResponse
import oms.urp3.dto.DashboardNationalSummaryResponse
import java.time.ZoneOffset

fun Route.dashboardRoutes() {
    get("/api/v1/dashboard/overview") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get
        val trancheNumber = call.request.queryParameters["tranche"]?.toIntOrNull()?.takeIf { it in 1..2 }
        // Dashboard aggregation opens several database registries.  Keep that
        // synchronous JDBC work off Ktor's request dispatcher so a slow
        // overview never stalls login, navigation, or lightweight API calls.
        val overview = withContext(Dispatchers.IO) {
            AppContainer.dashboardService.getOverview(null, trancheNumber)
        }
        call.respond(
            DashboardOverviewResponse(
                recentInspections = overview.recentInspections.map { it.report.toResponse(it.subprojectCode) },
                monthlyActPayments = overview.monthlyActPayments.map { MonthlyActPaymentResponse(it.month, it.amountEurCents, it.amountUahCents) },
                subprojectFunding = overview.subprojectFunding.map { SubprojectFundingResponse(it.projectUuid, it.name, it.region, it.amountUah, it.amountEur) },
                subprojectPaymentProgress = overview.subprojectPaymentProgress.map {
                    SubprojectPaymentProgressResponse(
                        it.projectUuid, it.code, it.region, it.eligibleContractValueUah, it.paidAmountUah, it.paymentPct
                    )
                },
                procurementStatusCounts = overview.procurementStatusCounts.map { DashboardMetricResponse(it.label, it.value) },
                signedContractsByType = overview.signedContractsByType.map {
                    DashboardContractTypeMetricResponse(it.type, it.contractsCount, it.contractAmountUah)
                },
                nationalSummary = DashboardNationalSummaryResponse(
                    overview.nationalSummary.subprojectsCount,
                    overview.nationalSummary.regionsCount,
                    overview.nationalSummary.municipalitiesCount,
                    overview.nationalSummary.settlementsCount,
                    overview.nationalSummary.approvedFundingUah,
                    overview.nationalSummary.approvedFundingEur,
                    overview.nationalSummary.signedContractsCount,
                    overview.nationalSummary.signedContractsAmountUah,
                    overview.nationalSummary.servicesPaidEurCents,
                    overview.nationalSummary.servicesPaidUahCents,
                    overview.nationalSummary.eibPaidUnderContractsUah
                )
            )
        )
    }

    get("/api/v1/dashboard") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val trancheNumber = call.request.queryParameters["tranche"]?.toIntOrNull()?.takeIf { it in 1..2 }
        val d = withContext(Dispatchers.IO) {
            AppContainer.dashboardService.get(null, trancheNumber)
        }
        val activities = if (session.roleCode.equals("ADMIN", ignoreCase = true)) {
            d.activities.map {
                ActivityResponse(
                    it.action,
                    it.entityType,
                    it.entityId,
                    it.userLogin,
                    it.createdAt.atOffset(ZoneOffset.UTC).toString()
                )
            }
        } else emptyList()
            call.respond(DashboardResponse(d.projectsTotal, d.projectsActive, d.projectsCompletedThisMonth, d.budgetPlanned, d.amountSpent, d.inspectionsTotal, d.pendingInspections, d.findingsTotal, d.recentInspections.map { it.toResponse() }, activities, d.monthlyActPayments.map { MonthlyActPaymentResponse(it.month, it.amountEurCents, it.amountUahCents) }, d.subprojectFunding.map { SubprojectFundingResponse(it.projectUuid, it.name, it.region, it.amountUah, it.amountEur) }, d.subprojectProgress.map { SubprojectProgressResponse(it.projectUuid, it.code, it.name, it.nameEn, it.region, it.completionPct) }, d.procurementStatusCounts.map { DashboardMetricResponse(it.label, it.value) }, d.monthlyInspectionCounts.map { DashboardMetricResponse(it.label, it.value) }, d.monthlyEshsViolations.map { DashboardMetricResponse(it.label, it.value) }, d.monthlyEquipmentPayments.map { MonthlyActPaymentResponse(it.month, it.amountEurCents, it.amountUahCents) }, d.monthlySignedConstructionContracts.map { DashboardMetricResponse(it.label, it.value) }))
    }
}
