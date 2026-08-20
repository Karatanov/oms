package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.ProjectMapPointResponse

fun Route.mapRoutes() {
    get("/api/v1/projects/map") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get
        call.respond(AppContainer.projectService.getAllProjects().filter {
            !session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true) ||
                AppContainer.projectService.isManagedBy(it.uuid.toString(), session.userId)
        }.map {
            ProjectMapPointResponse(
                uuid = it.uuid.toString(),
                name = it.name,
                latitude = it.latitude,
                longitude = it.longitude,
                status = it.status.name.lowercase()
            )
        })
    }
}
