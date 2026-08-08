package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.DashboardResponse
import oms.ufsi.dto.ActivityResponse
import oms.ufsi.dto.toResponse

fun Route.dashboardRoutes(){get("/api/v1/dashboard"){val d=AppContainer.dashboardService.get();call.respond(DashboardResponse(d.projectsTotal,d.projectsActive,d.budgetPlanned,d.amountSpent,d.inspectionsTotal,d.findingsTotal,d.recentInspections.map{it.toResponse()},d.activities.map{ActivityResponse(it.action,it.entityType,it.entityId,it.createdAt.toString())}))}}
