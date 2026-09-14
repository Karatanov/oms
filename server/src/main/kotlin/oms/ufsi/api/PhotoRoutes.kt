package oms.ufsi.api

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import oms.ufsi.config.AppContainer
import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportStatus
import oms.ufsi.dto.ErrorResponse
import oms.ufsi.dto.toResponse
import java.nio.file.Files

fun Route.photoRoutes() = route("/api/v1/inspection-reports/{reportUuid}/photos") {
    get {
        val report = call.photoReport() ?: return@get
        call.respond(hydrateEmbeddedSirPhotos(report).map { it.toResponse(report.uuid.toString()) })
    }

    post {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post
        val report = call.editablePhotoReport() ?: return@post
        val multipart = call.receiveMultipart()
        val uploaded = mutableListOf<oms.ufsi.domain.InspectionPhoto>()
        try {
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val name = part.originalFileName ?: throw IllegalArgumentException("File name is required.")
                    uploaded += AppContainer.inspectionPhotoService.upload(
                        report.id, name, part.contentType?.toString(),
                        part.provider().readRemaining(10_000_001).readByteArray()
                    )
                }
                part.dispose()
            }
            // A complete form submission may contain up to 30 photos. Syncing
            // the XLSX evidence sheet once per HTTP request (rather than once
            // per photo) removes repeated workbook parsing/writing and durable
            // blob persistence from the critical upload path.
            val photo = uploaded.firstOrNull() ?: throw IllegalArgumentException("Field file is required.")
            AppContainer.inspectionReportFileService.synchronizeManualPhotoSheet(
                report,
                AppContainer.inspectionPhotoService.list(report.id)
            ) { item ->
                AppContainer.inspectionPhotoService.resolveFile(item, thumbnail = false)
            }
            call.respond(HttpStatusCode.Created, photo.toResponse(report.uuid.toString()))
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid request."))
        }
    }

    put("{photoUuid}/main") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@put
        val report = call.editablePhotoReport() ?: return@put
        val photo = call.parameters["photoUuid"]?.let { AppContainer.inspectionPhotoService.setMain(report.id, it) }
            ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Photo not found."))
        call.respond(photo.toResponse(report.uuid.toString()))
    }

    delete("{photoUuid}") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@delete
        val report = call.editablePhotoReport() ?: return@delete
        val deleted = call.parameters["photoUuid"]?.let { AppContainer.inspectionPhotoService.delete(report.id, it) } ?: false
        if (!deleted) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Photo not found."))
        call.respond(HttpStatusCode.NoContent)
    }

    get("{photoUuid}/{kind}") {
        val report = call.photoReport() ?: return@get
        val photo = call.parameters["photoUuid"]?.let { AppContainer.inspectionPhotoService.get(report.id, it) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Photo not found."))
        val kind = call.parameters["kind"]
        val requestedPath = AppContainer.inspectionPhotoService.resolveFile(photo, thumbnail = kind == "thumbnail")
        // A thumbnail can be absent after an interrupted upload while the
        // original is available. Serving it here keeps the dashboard usable.
        val path = if (kind == "thumbnail" && requestedPath == null) {
            AppContainer.inspectionPhotoService.resolveFile(photo, thumbnail = false)
        } else {
            requestedPath
        }
        if (path == null || !Files.isRegularFile(path)) return@get call.respond(HttpStatusCode.NotFound)
        call.respondFile(path.toFile())
    }
}

/**
 * Older imported SIRs stored photographs only inside XLSX.  Materialise them
 * once on first access so that the same URLs work in both edit and preview
 * screens.  This is intentionally idempotent: a non-empty registry is never
 * touched, and an unreadable/unsupported workbook simply remains photo-less.
 */
internal fun hydrateEmbeddedSirPhotos(report: InspectionReport): List<oms.ufsi.domain.InspectionPhoto> {
    val photoService = AppContainer.inspectionPhotoService
    val existing = photoService.list(report.id)
    if (existing.isNotEmpty()) return existing
    AppContainer.inspectionReportFileService.extractEmbeddedPhotos(report)
        .take(30)
        .forEach { embedded ->
            runCatching {
                photoService.upload(report.id, embedded.originalName, embedded.contentType, embedded.bytes)
            }
        }
    return photoService.list(report.id)
}

private suspend fun ApplicationCall.photoReport(): InspectionReport? {
    val session = requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return null
    val report = parameters["reportUuid"]?.let { AppContainer.inspectionReportService.getByUuid(it) }
        ?: run { respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Inspection report not found.")); return null }
    val project = AppContainer.projectService.getAllProjects().firstOrNull { it.id == report.projectId }
        ?: run { respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found.")); return null }
    if (!requireProjectAccess(session, project.uuid.toString())) return null
    return report
}

private suspend fun ApplicationCall.editablePhotoReport(): InspectionReport? {
    // Photos are inspection evidence and are often received after the report
    // workflow is completed.  Authorised staff must therefore be able to add
    // and correct them without reopening the whole report.
    return photoReport()
}
