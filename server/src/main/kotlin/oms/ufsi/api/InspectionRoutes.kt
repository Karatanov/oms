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
import oms.ufsi.dto.*

/** REST endpoints for inspection findings. */
fun Route.inspectionRoutes() {
    post("/api/v1/projects/{projectUuid}/inspection-reports/manual") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post
        val projectUuid = call.parameters["projectUuid"] ?: return@post call.notFound("Project not found.")
        val project = AppContainer.projectService.getProjectByUuid(projectUuid) ?: return@post call.notFound("Project not found.")
        if (!call.requireProjectAccess(session, projectUuid)) return@post
        try {
            val report = AppContainer.inspectionReportFileService.createManual(project.id, call.receive(), session.userId)
            AppContainer.auditLogService.record(session.userId, "inspection_manual_created", "inspection_report", report.id)
            call.respond(HttpStatusCode.Created, report.toResponse())
        } catch (exception: IllegalArgumentException) { call.validationError(exception) }
    }
    post("/api/v1/projects/{projectUuid}/inspection-reports/import") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post
        val projectUuid = call.parameters["projectUuid"] ?: return@post call.notFound("Project not found.")
        val project = AppContainer.projectService.getProjectByUuid(projectUuid)
            ?: return@post call.notFound("Project not found.")
        if (!call.requireProjectAccess(session, projectUuid)) return@post
        val multipart = call.receiveMultipart()
        var imported: oms.ufsi.domain.InspectionReport? = null
        try {
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val name = part.originalFileName ?: throw IllegalArgumentException("File name is required.")
                    val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                    require(bytes.size <= 20_000_000) { "File size must not exceed 20 MB." }
                    imported = AppContainer.inspectionReportFileService.import(project.id, name, part.contentType?.toString(), java.io.ByteArrayInputStream(bytes), session.userId)
                }
                part.dispose()
            }
            val report = imported ?: throw IllegalArgumentException("Multipart field 'file' is required.")
            AppContainer.auditLogService.record(session.userId, "inspection_imported", "inspection_report", report.id)
            call.respond(HttpStatusCode.Created, report.toResponse())
        } catch (exception: IllegalArgumentException) {
            call.validationError(exception)
        }
    }

    get("/api/v1/inspection-reports/{reportUuid}/source-file") {
        val report = call.findReport() ?: return@get
        val file = AppContainer.inspectionReportFileService.getFile(report.id)
            ?: return@get call.notFound("Original SIR file not found.")
        val path = java.nio.file.Path.of(file.storagePath)
        if (!java.nio.file.Files.isRegularFile(path)) {
            return@get call.notFound("Original SIR file is unavailable.")
        }
        call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.originalName).toString())
        call.respondFile(path.toFile())
    }

    route("/api/v1/inspection-reports/{reportUuid}/findings") {
        get {
            val report = call.findReport() ?: return@get
            call.respond(
                AppContainer.inspectionFindingService
                    .getFindings(report.id)
                    .map { it.toResponse() }
            )
        }

        post {
            call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post
            val report = call.findReport() ?: return@post
            if (!call.requireEditableReport(report)) return@post
            val request = call.receive<CreateInspectionFindingRequest>()
            try {
                val finding = AppContainer.inspectionFindingService.createFinding(
                    inspectionReportId = report.id,
                    category = request.category,
                    severity = request.severity,
                    description = request.description,
                    recommendation = request.recommendation
                )
                call.respond(HttpStatusCode.Created, finding.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        get("{findingUuid}") {
            val report = call.findReport() ?: return@get
            val finding = call.findFinding(report.id) ?: return@get
            call.respond(finding.toResponse())
        }

        put("{findingUuid}") {
            call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@put
            val report = call.findReport() ?: return@put
            if (!call.requireEditableReport(report)) return@put
            val findingUuid = call.findingUuid() ?: return@put
            val request = call.receive<UpdateInspectionFindingRequest>()
            try {
                val finding = AppContainer.inspectionFindingService.updateFinding(
                    inspectionReportId = report.id,
                    uuid = findingUuid,
                    category = request.category,
                    severity = request.severity,
                    description = request.description,
                    recommendation = request.recommendation,
                    isResolved = request.isResolved
                ) ?: return@put call.notFound("Finding not found.")
                call.respond(finding.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        delete("{findingUuid}") {
            call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@delete
            val report = call.findReport() ?: return@delete
            if (!call.requireEditableReport(report)) return@delete
            val findingUuid = call.findingUuid() ?: return@delete
            if (!AppContainer.inspectionFindingService.deleteFinding(report.id, findingUuid)) {
                return@delete call.notFound("Finding not found.")
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/api/v1/inspection-reports/{reportUuid}") {
        get {
            val report = call.findReport() ?: return@get
            call.respond(report.toResponse())
        }

        delete {
            call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@delete
            val report = call.findReport() ?: return@delete
            if (!AppContainer.inspectionReportService.deleteReport(report.uuid.toString())) return@delete call.notFound("Inspection report not found.")
            call.respond(HttpStatusCode.NoContent)
        }

        put {
            call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@put
            val report = call.findReport() ?: return@put
            val request = call.receive<UpdateInspectionReportRequest>()
            try {
                val report = AppContainer.inspectionReportService.updateReport(
                    report.uuid.toString(), request.inspectionDate, request.summary
                ) ?: return@put call.notFound("Inspection report not found.")
                call.respond(report.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        patch("project") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@patch
            val sourceReport = call.findReport() ?: return@patch
            val request = call.receive<MoveInspectionReportRequest>()
            val targetProject = AppContainer.projectService.getProjectByUuid(request.projectUuid.trim())
                ?: return@patch call.notFound("Target project not found.")
            if (!call.requireProjectAccess(session, targetProject.uuid.toString())) return@patch
            val report = AppContainer.inspectionReportService.moveToProject(sourceReport.uuid.toString(), targetProject.id)
                ?: return@patch call.notFound("Inspection report not found.")
            call.respond(report.toResponse())
        }

        post("submit") {
            call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post
            val report = call.findReport() ?: return@post
            try {
                val report = AppContainer.inspectionReportService.submitReport(report.uuid.toString())
                    ?: return@post call.notFound("Inspection report not found.")
                call.respond(report.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        post("review") {
            call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
            val report = call.findReport() ?: return@post
            val request = call.receive<ReviewInspectionReportRequest>()
            try {
                val report = AppContainer.inspectionReportService.reviewReport(
                    report.uuid.toString(), request.action, request.rejectionReason
                ) ?: return@post call.notFound("Inspection report not found.")
                call.respond(report.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }
    }
}

private suspend fun ApplicationCall.findReport(): oms.ufsi.domain.InspectionReport? {
    val session = requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return null
    val report = parameters["reportUuid"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { AppContainer.inspectionReportService.getByUuid(it) }
        ?: run {
            notFound("Inspection report not found.")
            return null
        }
    val project = AppContainer.projectService.getAllProjects().firstOrNull { it.id == report.projectId }
        ?: run { notFound("Project not found."); return null }
    if (!requireProjectAccess(session, project.uuid.toString())) return null
    return report
}

private suspend fun ApplicationCall.requireEditableReport(report: oms.ufsi.domain.InspectionReport): Boolean {
    if (report.status != oms.ufsi.domain.InspectionReportStatus.COMPLETED) return true
    respond(HttpStatusCode.Conflict, ErrorResponse("REPORT_LOCKED", "Completed inspection reports cannot be changed."))
    return false
}

private suspend fun ApplicationCall.findFinding(reportId: Long) =
    findingUuid()?.let { uuid ->
        AppContainer.inspectionFindingService.getFinding(reportId, uuid)
            ?: run {
                notFound("Finding not found.")
                null
            }
    }

private suspend fun ApplicationCall.findingUuid(): String? =
    parameters["findingUuid"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: run {
            validationError(IllegalArgumentException("Finding UUID is required."))
            null
        }

private suspend fun ApplicationCall.validationError(exception: IllegalArgumentException) {
    respond(
        HttpStatusCode.BadRequest,
        ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid request.")
    )
}

private suspend fun ApplicationCall.notFound(message: String) {
    respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", message))
}
