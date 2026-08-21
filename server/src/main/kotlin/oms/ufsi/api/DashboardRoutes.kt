package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.DashboardResponse
import oms.ufsi.dto.ActivityResponse
import oms.ufsi.dto.toResponse
import oms.ufsi.dto.MonthlyActPaymentResponse

fun Route.dashboardRoutes() {
    get("/api/v1/dashboard") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val allowedProjectIds = if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true)) {
            AppContainer.projectService.getAllProjects().filter { AppContainer.projectService.isManagedBy(it.uuid.toString(), session.userId) }.map { it.id }.toSet()
        } else null
        val d = AppContainer.dashboardService.get(allowedProjectIds)
        call.respond(DashboardResponse(d.projectsTotal, d.projectsActive, d.projectsCompletedThisMonth, d.budgetPlanned, d.amountSpent, d.inspectionsTotal, d.pendingInspections, d.findingsTotal, d.recentInspections.map { it.toResponse() }, d.activities.map { ActivityResponse(it.action, it.entityType, it.entityId, it.userLogin, it.createdAt.toString()) }, d.monthlyActPayments.map { MonthlyActPaymentResponse(it.month, it.amount) }))
    }
}
