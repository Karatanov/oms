package oms.ufsi.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.ProjectMapPointResponse

fun Route.mapRoutes() {
    get("/api/v1/projects/map") {
        call.respond(AppContainer.projectService.getAllProjects().map {
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
