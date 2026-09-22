package oms.umitaf.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.ProjectMapPointResponse
import oms.umitaf.domain.ProjectType

fun Route.mapRoutes() {
    get("/api/v1/projects/map") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get
        val managedProjectIds = if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true)) {
            AppContainer.projectService.managedProjectIds(session.userId)
        } else null
        call.respond(AppContainer.projectService.getAllProjects().filter {
            it.projectType != ProjectType.PROJECT &&
                it.latitude != null && it.longitude != null &&
                (it.latitude != 0.0 || it.longitude != 0.0) &&
                (managedProjectIds == null || it.id in managedProjectIds)
        }.map {
            ProjectMapPointResponse(
                uuid = it.uuid.toString(),
                name = it.name,
                latitude = requireNotNull(it.latitude),
                longitude = requireNotNull(it.longitude),
                status = it.status.name.lowercase()
            )
        })
    }
}
