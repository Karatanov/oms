package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.DashboardResponse
import oms.ufsi.dto.DashboardOverviewResponse
import oms.ufsi.dto.ActivityResponse
import oms.ufsi.dto.toResponse
import oms.ufsi.dto.MonthlyActPaymentResponse
import oms.ufsi.dto.SubprojectFundingResponse
import oms.ufsi.dto.SubprojectProgressResponse
import oms.ufsi.dto.DashboardMetricResponse
import java.time.ZoneOffset

fun Route.dashboardRoutes() {
    get("/api/v1/dashboard/overview") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val allowedProjectIds = if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true)) {
            AppContainer.projectService.getAllProjects().filter { AppContainer.projectService.isManagedBy(it.uuid.toString(), session.userId) }
                .map { it.id }.toSet()
        } else null
        val overview = AppContainer.dashboardService.getOverview(allowedProjectIds)
        call.respond(
            DashboardOverviewResponse(
                recentInspections = overview.recentInspections.map { it.toResponse() },
                monthlyActPayments = overview.monthlyActPayments.map { MonthlyActPaymentResponse(it.month, it.amountEurCents) },
                subprojectFunding = overview.subprojectFunding.map { SubprojectFundingResponse(it.projectUuid, it.name, it.region, it.amountUah, it.amountEur) },
                subprojectProgress = overview.subprojectProgress.map { SubprojectProgressResponse(it.projectUuid, it.code, it.name, it.nameEn, it.region, it.completionPct) },
                procurementStatusCounts = overview.procurementStatusCounts.map { DashboardMetricResponse(it.label, it.value) }
            )
        )
    }

    get("/api/v1/dashboard") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val allowedProjectIds = if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true)) {
            AppContainer.projectService.getAllProjects().filter { AppContainer.projectService.isManagedBy(it.uuid.toString(), session.userId) }.map { it.id }.toSet()
        } else null
        val d = AppContainer.dashboardService.get(allowedProjectIds)
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
            call.respond(DashboardResponse(d.projectsTotal, d.projectsActive, d.projectsCompletedThisMonth, d.budgetPlanned, d.amountSpent, d.inspectionsTotal, d.pendingInspections, d.findingsTotal, d.recentInspections.map { it.toResponse() }, activities, d.monthlyActPayments.map { MonthlyActPaymentResponse(it.month, it.amountEurCents) }, d.subprojectFunding.map { SubprojectFundingResponse(it.projectUuid, it.name, it.region, it.amountUah, it.amountEur) }, d.subprojectProgress.map { SubprojectProgressResponse(it.projectUuid, it.code, it.name, it.nameEn, it.region, it.completionPct) }, d.procurementStatusCounts.map { DashboardMetricResponse(it.label, it.value) }, d.monthlyInspectionCounts.map { DashboardMetricResponse(it.label, it.value) }, d.monthlyEshsViolations.map { DashboardMetricResponse(it.label, it.value) }, d.monthlyEquipmentPayments.map { MonthlyActPaymentResponse(it.month, it.amountEurCents) }, d.monthlySignedConstructionContracts.map { DashboardMetricResponse(it.label, it.value) }))
    }
}
