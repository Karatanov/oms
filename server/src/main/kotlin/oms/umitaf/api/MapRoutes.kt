package oms.umitaf.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.ProjectMapPointResponse
import oms.umitaf.domain.ProjectType
import oms.umitaf.domain.ProjectStatus

fun Route.mapRoutes() {
    get("/api/v1/projects/map") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get
        call.respond(AppContainer.projectService.getAllProjects().filter {
            it.projectType != ProjectType.PROJECT &&
                it.status != ProjectStatus.ARCHIVED &&
                it.latitude != null && it.longitude != null &&
                (it.latitude != 0.0 || it.longitude != 0.0)
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
