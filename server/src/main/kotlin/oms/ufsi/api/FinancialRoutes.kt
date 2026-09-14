package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.*

fun Route.financialRoutes() {
    get("/api/v1/financials") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val records = AppContainer.financialRecordService.getAll()
        // An empty registry needs no project lookup.  This is common in a new
        // deployment and avoids loading the full project tree just to return [].
        if (records.isEmpty()) {
            call.respond(emptyList<FinancialRecordListItemResponse>())
            return@get
        }
        val managedProjectIds = if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true)) {
            AppContainer.projectService.managedProjectIds(session.userId)
        } else null
        val accessibleProjects = AppContainer.projectService.getAllProjects().filter { project ->
            managedProjectIds == null || project.id in managedProjectIds
        }
        val projectUuidsById = accessibleProjects.associate { it.id to it.uuid.toString() }
        call.respond(
            records.mapNotNull { record ->
                projectUuidsById[record.projectId]?.let { projectUuid ->
                    FinancialRecordListItemResponse(projectUuid, record.toResponse())
                }
            }
        )
    }

    route("/api/v1/projects/{projectUuid}/financials") {
        get {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
            val project = call.project() ?: return@get
            if (!call.requireProjectAccess(session, project.uuid.toString())) return@get
            val service = AppContainer.financialRecordService
            try {
                val summary = service.summary(project)
                val records = service.filtered(
                    project.id,
                    call.request.queryParameters["record_type"],
                    call.request.queryParameters["date_from"],
                    call.request.queryParameters["date_to"]
                )
                call.respond(
                    FinancialRecordListResponse(
                        data = records.map { it.toResponse() },
                        summary = FinancialSummaryResponse(
                            summary.budgetPlanned,
                            summary.constructionContractAmount,
                            summary.amountSpent,
                            summary.financialDocumentsAmount,
                            summary.budgetRemaining,
                            summary.completionPct,
                            summary.financialCompletionPct
                        )
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.financeError(e)
            }
        }
        post {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
            val project = call.project() ?: return@post
            if (!call.requireProjectAccess(session, project.uuid.toString())) return@post
            val request = call.receive<CreateFinancialRecordRequest>()
            try { val record = AppContainer.financialRecordService.create(project.id, request.recordType, request.referenceNumber, request.amount, request.currency, request.recordDate, request.paymentDate, request.description, request.milestone, request.paymentPurpose); AppContainer.auditLogService.record(session.userId, "financial_record_created", "financial_record", record.id); call.respond(HttpStatusCode.Created, record.toResponse()) } catch (e: IllegalArgumentException) { call.financeError(e) }
        }
        post("import") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
            val project = call.project() ?: return@post
            if (!call.requireProjectAccess(session, project.uuid.toString())) return@post
            var imported: oms.ufsi.service.FinancialImportResult? = null

            try {
                call.receiveMultipart().forEachPart { part ->
                    if (part is PartData.FileItem && part.name == "file") {
                        require((part.originalFileName ?: "").substringAfterLast('.', "").lowercase() in setOf("xls", "xlsx")) { "Only XLS or XLSX files are supported." }
                        val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                        require(bytes.size <= 20_000_000) { "File size must not exceed 20 MB." }
                        imported = AppContainer.financialRecordService.importWorkbook(
                            project.id,
                            java.io.ByteArrayInputStream(bytes)
                        )
                    }
                    part.dispose()
                }
                val result = imported ?: throw IllegalArgumentException("Field file is required.")
                if (result.imported > 0) AppContainer.auditLogService.record(session.userId, "financial_records_imported", "project", project.id)
                call.respond(
                    FinancialImportResponse(
                        imported = result.imported,
                        skipped = result.skipped,
                        errors = result.errors.map { FinancialImportErrorResponse(it.row, it.field, it.message) }
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.financeError(e)
            }
        }
        get("export") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
            val project = call.project() ?: return@get
            if (!call.requireProjectAccess(session, project.uuid.toString())) return@get
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=financials.xlsx")
            call.respondOutputStream(
                ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            ) {
                AppContainer.financialRecordService.exportXlsx(project.id, this)
            }
        }
        get("{recordUuid}") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
            val project = call.project() ?: return@get
            if (!call.requireProjectAccess(session, project.uuid.toString())) return@get
            val record = call.parameters["recordUuid"]?.let { AppContainer.financialRecordService.get(project.id, it) } ?: return@get call.financeNotFound("Financial record not found.")
            call.respond(record.toResponse())
        }
        put("{recordUuid}") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@put
            val project = call.project() ?: return@put
            if (!call.requireProjectAccess(session, project.uuid.toString())) return@put
            val uuid = call.parameters["recordUuid"] ?: return@put call.financeNotFound("Financial record not found.")
            val request = call.receive<UpdateFinancialRecordRequest>()
            try {
                val target = request.targetProjectUuid?.trim()?.takeIf { it.isNotEmpty() }?.let { targetUuid ->
                    AppContainer.projectService.getProjectByUuid(targetUuid)
                        ?: return@put call.financeNotFound("Target project not found.")
                } ?: project
                if (!call.requireProjectAccess(session, target.uuid.toString())) return@put
                if (target.id != project.id && !AppContainer.financialRecordService.move(project.id, uuid, target.id)) {
                    return@put call.financeNotFound("Financial record not found.")
                }
                val record = AppContainer.financialRecordService.update(target.id, uuid, request.recordType, request.referenceNumber, request.amount, request.currency, request.recordDate, request.paymentDate, request.description, request.milestone, request.paymentPurpose)
                    ?: return@put call.financeNotFound("Financial record not found.")
                if (target.id != project.id) AppContainer.auditLogService.record(session.userId, "financial_record_moved", "financial_record", record.id)
                call.respond(record.toResponse())
            } catch (e: IllegalArgumentException) { call.financeError(e) }
        }
        delete("{recordUuid}") {
            val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@delete
            val project = call.project() ?: return@delete
            if (!call.requireProjectAccess(session, project.uuid.toString())) return@delete
            val uuid = call.parameters["recordUuid"] ?: return@delete call.financeNotFound("Financial record not found.")
            if (!AppContainer.financialRecordService.delete(project.id, uuid)) return@delete call.financeNotFound("Financial record not found.")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.project() = parameters["projectUuid"]?.let { AppContainer.projectService.getProjectByUuid(it) } ?: run { financeNotFound("Project not found."); null }
private suspend fun io.ktor.server.application.ApplicationCall.financeError(e: IllegalArgumentException) { respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", e.message ?: "Invalid request.")) }
private suspend fun io.ktor.server.application.ApplicationCall.financeNotFound(message: String) { respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", message)) }
