package oms.umitaf.api

import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import oms.umitaf.config.AppContainer
import oms.umitaf.domain.ProcurementRecord
import oms.umitaf.dto.ErrorResponse
import oms.umitaf.dto.ProcurementRecordResponse
import oms.umitaf.service.ProcurementTablePdfExportService
import kotlinx.serialization.Serializable

fun Route.procurementRoutes() {
    get("/api/v1/procurements") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        call.respond(AppContainer.procurementService.getAll().map(ProcurementRecord::toResponse))
    }

    /**
     * Exports the entire filtered and sorted client registry. Record IDs are
     * submitted in UI order rather than only the current pagination page.
     */
    get("/api/v1/procurements/export-table-pdf") {
        call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val requestedIds = call.request.queryParameters.getAll("id")
            ?.mapNotNull { it.toLongOrNull() }
            ?.distinct()
            .orEmpty()
        val hasExplicitSelection = requestedIds.isNotEmpty() ||
            call.request.queryParameters["empty"]?.toBooleanStrictOrNull() == true
        val allRecords = AppContainer.procurementService.getAll()
        val recordsById = allRecords.associateBy { it.id }
        val records = if (hasExplicitSelection) requestedIds.mapNotNull(recordsById::get) else allRecords
        val language = if (call.request.queryParameters["lang"].equals("en", ignoreCase = true)) {
            ProcurementTablePdfExportService.Language.EN
        } else {
            ProcurementTablePdfExportService.Language.UK
        }
        val filterSummary = call.request.queryParameters["filters"]?.take(500)
        val pdf = ProcurementTablePdfExportService().export(records, language, filterSummary)
        call.response.header("Content-Disposition", "attachment; filename=oms-procurement.pdf")
        call.respondBytes(pdf, ContentType.Application.Pdf)
    }

    post("/api/v1/procurements/refresh") {
        call.requireRole("ADMIN") ?: return@post
        call.respondRefreshSafely(HttpStatusCode.Accepted) {
            AppContainer.procurementService.startApprovedSourceRefresh().toResponse()
        }
    }

    post("/api/v1/procurements/refresh/upload") {
        call.requireRole("ADMIN") ?: return@post
        var workbook: ByteArray? = null
        try {
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val name = part.originalFileName.orEmpty()
                    require(name.endsWith(".xlsx", true) || name.endsWith(".xls", true)) {
                        "Select an XLS or XLSX procurement workbook."
                    }
                    val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                    require(bytes.size <= 20_000_000) { "The approved procurement workbook must not exceed 20 MB." }
                    workbook = bytes
                }
                part.dispose()
            }
            val status = AppContainer.procurementService.startApprovedWorkbookRefresh(
                workbook ?: throw IllegalArgumentException("Multipart field 'file' is required.")
            )
            call.respond(HttpStatusCode.Accepted, status.toResponse())
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid procurement workbook."))
        }
    }

    get("/api/v1/procurements/refresh/{id}") {
        call.requireRole("ADMIN") ?: return@get
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Refresh ID is required."))
        val status = AppContainer.procurementService.getApprovedSourceRefresh(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Procurement refresh not found."))
        call.respond(status.toResponse())
    }

    post("/api/v1/procurements/refresh/{id}/cancel") {
        call.requireRole("ADMIN") ?: return@post
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Refresh ID is required."))
        val status = AppContainer.procurementService.cancelApprovedSourceRefresh(id)
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Procurement refresh not found."))
        call.respond(status.toResponse())
    }
}

@Serializable
private data class ProcurementRefreshResponse(
    val id: String,
    val status: String,
    val progress: Int,
    val phase: String,
    val importedCount: Int? = null,
    val error: String? = null
)

private fun oms.umitaf.service.ProcurementService.RefreshStatus.toResponse() = ProcurementRefreshResponse(
    id = id, status = status, progress = progress, phase = phase, importedCount = importedCount, error = error
)

/**
 * Sends the only success payload emitted by the procurement refresh endpoint.
 *
 * Keeping this concrete avoids Kotlin/Ktor response-type inference at the route
 * boundary, which previously made the production build fail before deployment.
 */
private suspend fun io.ktor.server.application.ApplicationCall.respondRefreshSafely(
    success: HttpStatusCode,
    block: () -> ProcurementRefreshResponse
) {
    try {
        respond(success, block())
    } catch (exception: IllegalStateException) {
        respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", exception.message ?: "Procurement refresh is already running."))
    } catch (exception: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid procurement data."))
    }
}

private fun ProcurementRecord.toResponse() = ProcurementRecordResponse(
    id = id, batchId = batchId, oblastName = oblastName, oblastId = oblastId,
    subProjectId = subProjectId, subProjectLotId = subProjectLotId, purchaseStatus = purchaseStatus,
    tenderId = tenderId, prozorroTenderId = prozorroTenderId, contractorNameUkr = contractorNameUkr,
    contractorNameEng = contractorNameEng, contractorId = contractorId, contractDate = contractDate?.toString(),
    contractEndDate = contractEndDate?.toString(), actualisedContractEndDate = actualisedContractEndDate?.toString(), contractDurationMonths = contractDurationMonths,
    contractAmountUah = contractAmountUah, contractAmountUahWithoutVat = contractAmountUahWithoutVat, contractAmountEur = contractAmountEur,
    financingContractDifferencePct = financingContractDifferencePct, promotorName = promotorName, fbName = fbName,
    subprojectNameUk = subprojectNameUk, subprojectNameEn = subprojectNameEn,
    sourceContractType = sourceContractType,
    subprojectTotalCostUah = subprojectTotalCostUah, subprojectEibFinancingUah = subprojectEibFinancingUah,
    subprojectLocalFinancingUah = subprojectLocalFinancingUah, estimatedTotalEur = estimatedTotalEur,
    estimatedTotalUah = estimatedTotalUah, estimatedEibEur = estimatedEibEur, estimatedEibUah = estimatedEibUah,
    estimatedLocalEur = estimatedLocalEur, estimatedLocalUah = estimatedLocalUah,
    procurementMethod = procurementMethod, tenderDocumentType = tenderDocumentType, publishedInOjeu = publishedInOjeu,
    estimatedProzorroDate = estimatedProzorroDate?.toString(), estimatedBidSubmissionDate = estimatedBidSubmissionDate?.toString(),
    estimatedContractDate = estimatedContractDate?.toString(), estimatedContractEndDate = estimatedContractEndDate?.toString(),
    localFinancingPct = localFinancingPct, tenderAttemptCount = tenderAttemptCount, pigViolations = pigViolations,
    dreamCoFinancingPct = dreamCoFinancingPct, realLocalCoFinancingPct = realLocalCoFinancingPct,
    realEibFinancingUah = realEibFinancingUah, bankGuarantee = bankGuarantee,
    contractLink = contractLink,
    comments = comments, sourceStatusCode = sourceStatusCode, projectId = projectId
)
