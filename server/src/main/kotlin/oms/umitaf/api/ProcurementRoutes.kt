package oms.umitaf.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import oms.umitaf.config.AppContainer
import oms.umitaf.domain.ProcurementRecord
import oms.umitaf.dto.ErrorResponse
import oms.umitaf.dto.ProcurementRecordRequest
import oms.umitaf.dto.ProcurementRecordResponse

fun Route.procurementRoutes() {
    get("/api/v1/procurements") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        call.respond(AppContainer.procurementService.getAll().map(ProcurementRecord::toResponse))
    }

    post("/api/v1/procurements") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
        val request = call.receive<ProcurementRecordRequest>()
        call.respondSafely(HttpStatusCode.Created) { AppContainer.procurementService.create(request).toResponse() }
    }

    patch("/api/v1/procurements/{id}") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@patch
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Procurement record ID is required."))
        val request = call.receive<ProcurementRecordRequest>()
        call.respondSafely(HttpStatusCode.OK) {
            AppContainer.procurementService.update(id, request)?.toResponse()
                ?: throw NoSuchElementException("Procurement record not found.")
        }
    }

    delete("/api/v1/procurements/{id}") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@delete
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Procurement record ID is required."))
        if (!AppContainer.procurementService.delete(id)) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Procurement record not found."))
        } else call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondSafely(
    success: HttpStatusCode,
    block: () -> ProcurementRecordResponse
) {
    try {
        respond(success, block())
    } catch (_: NoSuchElementException) {
        respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Procurement record not found."))
    } catch (exception: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid procurement data."))
    }
}

private fun ProcurementRecord.toResponse() = ProcurementRecordResponse(
    id = id, batchId = batchId, oblastName = oblastName, oblastId = oblastId,
    subProjectId = subProjectId, subProjectLotId = subProjectLotId, purchaseStatus = purchaseStatus,
    tenderId = tenderId, prozorroTenderId = prozorroTenderId, contractorNameUkr = contractorNameUkr,
    contractorNameEng = contractorNameEng, contractorId = contractorId, contractDate = contractDate?.toString(),
    contractEndDate = contractEndDate?.toString(), contractDurationMonths = contractDurationMonths,
    contractAmountUah = contractAmountUah, contractAmountEur = contractAmountEur,
    financingContractDifferencePct = financingContractDifferencePct, promotorName = promotorName,
    subprojectNameUk = subprojectNameUk, subprojectNameEn = subprojectNameEn,
    sourceContractType = sourceContractType,
    subprojectTotalCostUah = subprojectTotalCostUah, subprojectEibFinancingUah = subprojectEibFinancingUah,
    subprojectLocalFinancingUah = subprojectLocalFinancingUah, estimatedTotalEur = estimatedTotalEur,
    estimatedTotalUah = estimatedTotalUah, estimatedEibEur = estimatedEibEur, estimatedEibUah = estimatedEibUah,
    estimatedLocalEur = estimatedLocalEur, estimatedLocalUah = estimatedLocalUah,
    procurementMethod = procurementMethod, tenderDocumentType = tenderDocumentType, publishedInOjeu = publishedInOjeu,
    estimatedProzorroDate = estimatedProzorroDate?.toString(), estimatedBidSubmissionDate = estimatedBidSubmissionDate?.toString(),
    estimatedContractDate = estimatedContractDate?.toString(), estimatedContractEndDate = estimatedContractEndDate?.toString(),
    localFinancingPct = localFinancingPct, comments = comments, sourceStatusCode = sourceStatusCode, projectId = projectId
)
