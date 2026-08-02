package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.*

fun Route.financialRoutes() {
    route("/api/v1/projects/{projectUuid}/financials") {
        get {
            val project = call.project() ?: return@get
            val service = AppContainer.financialRecordService
            val summary = service.summary(project)
            call.respond(mapOf("data" to service.getAll(project.id).map { it.toResponse() }, "summary" to FinancialSummaryResponse(summary.budgetPlanned, summary.amountSpent, summary.budgetRemaining, summary.completionPct)))
        }
        post {
            call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
            val project = call.project() ?: return@post
            val request = call.receive<CreateFinancialRecordRequest>()
            try { call.respond(HttpStatusCode.Created, AppContainer.financialRecordService.create(project.id, request.recordType, request.referenceNumber, request.amount, request.currency, request.recordDate, request.paymentDate, request.description, request.milestone).toResponse()) } catch (e: IllegalArgumentException) { call.financeError(e) }
        }
        get("{recordUuid}") {
            val project = call.project() ?: return@get
            val record = call.parameters["recordUuid"]?.let { AppContainer.financialRecordService.get(project.id, it) } ?: return@get call.financeNotFound("Financial record not found.")
            call.respond(record.toResponse())
        }
        put("{recordUuid}") {
            call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@put
            val project = call.project() ?: return@put
            val uuid = call.parameters["recordUuid"] ?: return@put call.financeNotFound("Financial record not found.")
            val request = call.receive<UpdateFinancialRecordRequest>()
            try { val record = AppContainer.financialRecordService.update(project.id, uuid, request.recordType, request.referenceNumber, request.amount, request.currency, request.recordDate, request.paymentDate, request.description, request.milestone) ?: return@put call.financeNotFound("Financial record not found."); call.respond(record.toResponse()) } catch (e: IllegalArgumentException) { call.financeError(e) }
        }
        delete("{recordUuid}") {
            call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@delete
            val project = call.project() ?: return@delete
            val uuid = call.parameters["recordUuid"] ?: return@delete call.financeNotFound("Financial record not found.")
            if (!AppContainer.financialRecordService.delete(project.id, uuid)) return@delete call.financeNotFound("Financial record not found.")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.project() = parameters["projectUuid"]?.let { AppContainer.projectService.getProjectByUuid(it) } ?: run { financeNotFound("Project not found."); null }
private suspend fun io.ktor.server.application.ApplicationCall.financeError(e: IllegalArgumentException) { respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", e.message ?: "Invalid request.")) }
private suspend fun io.ktor.server.application.ApplicationCall.financeNotFound(message: String) { respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", message)) }
