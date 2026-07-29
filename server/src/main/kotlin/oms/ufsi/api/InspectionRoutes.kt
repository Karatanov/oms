package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.*

/** REST endpoints for inspection findings. */
fun Route.inspectionRoutes() {
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
            val report = call.findReport() ?: return@post
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
            val report = call.findReport() ?: return@put
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
            val report = call.findReport() ?: return@delete
            val findingUuid = call.findingUuid() ?: return@delete
            if (!AppContainer.inspectionFindingService.deleteFinding(report.id, findingUuid)) {
                return@delete call.notFound("Finding not found.")
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/api/v1/inspection-reports/{reportUuid}") {
        put {
            val reportUuid = call.parameters["reportUuid"] ?: return@put call.notFound("Inspection report not found.")
            val request = call.receive<UpdateInspectionReportRequest>()
            try {
                val report = AppContainer.inspectionReportService.updateReport(
                    reportUuid, request.inspectionDate, request.completionPct, request.summary
                ) ?: return@put call.notFound("Inspection report not found.")
                call.respond(report.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        post("submit") {
            val reportUuid = call.parameters["reportUuid"] ?: return@post call.notFound("Inspection report not found.")
            try {
                val report = AppContainer.inspectionReportService.submitReport(reportUuid)
                    ?: return@post call.notFound("Inspection report not found.")
                call.respond(report.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }

        post("review") {
            val reportUuid = call.parameters["reportUuid"] ?: return@post call.notFound("Inspection report not found.")
            val request = call.receive<ReviewInspectionReportRequest>()
            try {
                val report = AppContainer.inspectionReportService.reviewReport(
                    reportUuid, request.action, request.rejectionReason
                ) ?: return@post call.notFound("Inspection report not found.")
                call.respond(report.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.validationError(exception)
            }
        }
    }
}

private suspend fun ApplicationCall.findReport() =
    parameters["reportUuid"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { AppContainer.inspectionReportService.getByUuid(it) }
        ?: run {
            notFound("Inspection report not found.")
            null
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
