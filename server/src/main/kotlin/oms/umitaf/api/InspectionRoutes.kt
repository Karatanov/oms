package oms.umitaf.api

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.*

private const val AUTO_HSE_FINDING_CATEGORY = "hse_sir_auto"

/** Keeps the H&S chart in step with a manually edited SIR without touching user-created findings. */
private fun synchronizeManualHseFindings(reportId: Long, observations: List<ManualHseObservation>) {
    val entries = observations
        // A checked standard observation means compliance.  An unchecked
        // answer and a custom free-text observation both describe an actual
        // issue and therefore must contribute to the ESHS violation chart.
        .filter { !it.answer.equals("yes", ignoreCase = true) }
        .map { it.observation to it.comment }
    AppContainer.inspectionFindingService.replaceCategory(reportId, AUTO_HSE_FINDING_CATEGORY, "medium", entries)
}

/** REST endpoints for inspection findings. */
fun Route.inspectionRoutes() {
    get("/api/v1/inspection-reports") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val reports = AppContainer.inspectionReportService.getAllReports()
        // The registry is often empty on a new deployment.  Avoid a full
        // project lookup when there are no rows that need access filtering.
        if (reports.isEmpty()) {
            call.respond(emptyList<InspectionReportListItemResponse>())
            return@get
        }
        val managedProjectIds = if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true)) {
            AppContainer.projectService.managedProjectIds(session.userId)
        } else null
        val projects = AppContainer.projectService.getAllProjects().filter { project ->
            managedProjectIds == null || project.id in managedProjectIds
        }
        val projectUuidsById = projects.associate { it.id to it.uuid.toString() }
        call.respond(
            reports.mapNotNull { report ->
                projectUuidsById[report.projectId]?.let { projectUuid ->
                    InspectionReportListItemResponse(projectUuid, report.toResponse())
                }
            }
        )
    }

    get("/api/v1/inspection-reports/analytics") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val allowedProjectIds = if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true)) {
            AppContainer.projectService.managedProjectIds(session.userId)
        } else null
        val analytics = AppContainer.inspectionAnalyticsService.get(allowedProjectIds)
        call.respond(
            InspectionAnalyticsResponse(
                monthlyInspectionCounts = analytics.monthlyInspectionCounts.map { DashboardMetricResponse(it.label, it.value) },
                monthlyEshsViolations = analytics.monthlyEshsViolations.map { DashboardMetricResponse(it.label, it.value) }
            )
        )
    }

    post("/api/v1/projects/{projectUuid}/inspection-reports/manual") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post
        val projectUuid = call.parameters["projectUuid"] ?: return@post call.notFound("Project not found.")
        val project = AppContainer.projectService.getProjectByUuid(projectUuid) ?: return@post call.notFound("Project not found.")
        if (!call.requireProjectAccess(session, projectUuid)) return@post
        try {
            val request = call.receive<CreateManualInspectionReportRequest>()
            val draft = AppContainer.inspectionReportFileService.createManual(project, request, session.userId)
            // A completed manual form is submitted immediately.  The separate
            // "Save draft" action creates a draft through its own endpoint.
            val report = AppContainer.inspectionReportService.submitReport(draft.uuid.toString())
                ?: error("Created inspection report is unavailable.")
            synchronizeManualHseFindings(report.id, request.hseObservations)
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
        var imported: oms.umitaf.domain.InspectionReport? = null
        try {
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val name = part.originalFileName ?: throw IllegalArgumentException("File name is required.")
                    val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                    require(bytes.size <= 20_000_000) { "File size must not exceed 20 MB." }
                    // Keep the import request limited to accepting the
                    // workbook. A Photo Attachment sheet can contain 30
                    // high-resolution images; extracting, thumbnailing and
                    // persisting them here turns one upload into dozens of
                    // slow storage writes. The photo endpoint materialises
                    // them lazily on the first edit/preview instead.
                    imported = AppContainer.inspectionReportFileService
                        .import(project.id, name, part.contentType?.toString(), java.io.ByteArrayInputStream(bytes), session.userId)
                }
                part.dispose()
            }
            val report = imported ?: throw IllegalArgumentException("Multipart field 'file' is required.")
            // Imported workbooks do not have user-created findings. Extract the
            // H&S checklist once at import time and mirror only non-compliant
            // rows into the analytics finding set.  This keeps the ESHS chart
            // consistent with manually created reports without making every
            // dashboard read reopen XLSX files.
            val importedHse = AppContainer.inspectionReportFileService
                .healthSafetyObservations(report.id)
                .map { ManualHseObservation(it.observation, it.answer, it.comment) }
            synchronizeManualHseFindings(report.id, importedHse)
            AppContainer.auditLogService.record(session.userId, "inspection_imported", "inspection_report", report.id)
            call.respond(HttpStatusCode.Created, report.toResponse())
        } catch (exception: IllegalArgumentException) {
            call.validationError(exception)
        }
    }

    get("/api/v1/inspection-reports/{reportUuid}/source-file") {
        val report = call.findReport() ?: return@get
        // The photo route synchronizes a manual workbook immediately after a
        // successful batch upload. Rebuilding it again on every download made
        // a read-only request parse, write and persist the same XLSX.
        val file = AppContainer.inspectionReportFileService.getFile(report.id)
            ?: return@get call.notFound("Original SIR file not found.")
        val path = AppContainer.inspectionReportFileService.resolveFile(file)
            ?: return@get call.notFound("Original SIR file is unavailable.")
        call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.originalName).toString())
        call.respondFile(path.toFile())
    }

    get("/api/v1/inspection-reports/{reportUuid}/preview") {
        val report = call.findReport() ?: return@get
        try {
            // Apache POI opens the complete XLSX package and can spend seconds
            // reading embedded images. Never run that blocking work on Ktor's
            // request dispatcher: otherwise one report preview stalls the
            // entire web application, including navigation requests.
            val preview = withContext(Dispatchers.IO) {
                AppContainer.inspectionReportFileService.preview(report)
            }
            call.respond(preview)
        } catch (exception: IllegalArgumentException) {
            call.validationError(exception)
        }
    }

    put("/api/v1/inspection-reports/{reportUuid}/source-file") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@put
        val report = call.findReport() ?: return@put
        val multipart = call.receiveMultipart()
        var replacement: oms.umitaf.domain.InspectionReportFile? = null
        try {
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val name = part.originalFileName ?: throw IllegalArgumentException("File name is required.")
                    val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                    require(bytes.size <= 20_000_000) { "File size must not exceed 20 MB." }
                    replacement = AppContainer.inspectionReportFileService.replace(
                        report,
                        name,
                        part.contentType?.toString(),
                        java.io.ByteArrayInputStream(bytes)
                    )
                }
                part.dispose()
            }
            replacement ?: throw IllegalArgumentException("Multipart field 'file' is required.")
            val replacementHse = AppContainer.inspectionReportFileService
                .healthSafetyObservations(report.id)
                .map { ManualHseObservation(it.observation, it.answer, it.comment) }
            synchronizeManualHseFindings(report.id, replacementHse)
            AppContainer.auditLogService.record(session.userId, "inspection_source_file_replaced", "inspection_report", report.id)
            call.respond(HttpStatusCode.NoContent)
        } catch (exception: IllegalArgumentException) {
            call.validationError(exception)
        }
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

        get("manual") {
            val report = call.findReport() ?: return@get
            try {
                val project = AppContainer.projectService.getAllProjects().firstOrNull { it.id == report.projectId }
                    ?: return@get call.notFound("Project not found.")
                // Imported reports are parsed from their source workbook on
                // first access. Keep this disk/POI operation off the request
                // dispatcher so that opening a report cannot freeze the SPA.
                val manual = withContext(Dispatchers.IO) {
                    AppContainer.inspectionReportFileService.readManual(report)
                }
                call.respond(
                    ManualInspectionReportEditorResponse(
                        project.uuid.toString(), report.status.name.lowercase(),
                        manual
                    )
                )
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        put("manual") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@put
            val report = call.findReport() ?: return@put
            val request = call.receive<UpdateManualInspectionReportRequest>()
            val targetProject = AppContainer.projectService.getProjectByUuid(request.projectUuid.trim())
                ?: return@put call.notFound("Target project not found.")
            if (!call.requireProjectAccess(session, targetProject.uuid.toString())) return@put
            try {
                val moved = if (report.projectId == targetProject.id) report else {
                    AppContainer.inspectionReportService.moveToProject(report.uuid.toString(), targetProject.id)
                        ?: return@put call.notFound("Inspection report not found.")
                }
                val updated = AppContainer.inspectionReportFileService.updateManual(moved, targetProject, request.manual)
                synchronizeManualHseFindings(updated.id, request.manual.hseObservations)
                val finalReport = if (request.status != null && request.status != updated.status.name.lowercase()) {
                    if (!session.roleCode.equals("ADMIN", true) && !session.roleCode.equals("PROJECT_MANAGER", true)) {
                        throw IllegalArgumentException("Only an administrator or project manager can change report status.")
                    }
                    AppContainer.inspectionReportService.setStatus(updated.uuid.toString(), request.status)
                        ?: return@put call.notFound("Inspection report not found.")
                } else updated
                AppContainer.auditLogService.record(session.userId, "inspection_manual_updated", "inspection_report", updated.id)
                call.respond(finalReport.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        put {
            call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@put
            val report = call.findReport() ?: return@put
            val request = call.receive<UpdateInspectionReportRequest>()
            try {
                val report = AppContainer.inspectionReportService.updateReport(
                    report.uuid.toString(), request.inspectionDate, request.summary,
                    request.reportCode, request.inspectionType, request.latitude, request.longitude
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

        patch("status") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@patch
            val existingReport = call.findReport() ?: return@patch
            val request = call.receive<UpdateInspectionReportStatusRequest>()
            try {
                val report = AppContainer.inspectionReportService.setStatus(
                    existingReport.uuid.toString(), request.status
                ) ?: return@patch call.notFound("Inspection report not found.")
                AppContainer.auditLogService.record(session.userId, "inspection_status_updated", "inspection_report", report.id)
                call.respond(report.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
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

private suspend fun ApplicationCall.findReport(): oms.umitaf.domain.InspectionReport? {
    val session = requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return null
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

private suspend fun ApplicationCall.requireEditableReport(report: oms.umitaf.domain.InspectionReport): Boolean {
    if (report.status != oms.umitaf.domain.InspectionReportStatus.COMPLETED) return true
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
