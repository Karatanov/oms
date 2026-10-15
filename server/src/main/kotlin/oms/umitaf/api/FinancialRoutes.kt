package oms.umitaf.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import oms.umitaf.config.AppContainer
import oms.umitaf.dto.*
import oms.umitaf.domain.FinancialRecordType
import oms.umitaf.domain.ProjectType
import oms.umitaf.service.FinancialTablePdfExportService
import kotlin.math.roundToLong

fun Route.financialRoutes() {
    val financialTablePdfExportService = FinancialTablePdfExportService()
    get("/api/v1/financials/progress") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val projects = AppContainer.projectService.getAllProjects()
        val byId = projects.associateBy { it.id }
        val subprojects = projects.filter { it.projectType == ProjectType.SUBPROJECT }
        fun parentSubproject(projectId: Long): oms.umitaf.domain.Project? {
            var current = byId[projectId] ?: return null
            while (current.projectType != ProjectType.SUBPROJECT) {
                current = current.parentProjectId?.let(byId::get) ?: return null
            }
            return current
        }
        val completedBySubprojectId = AppContainer.financialRecordService.getAll()
            .asSequence()
            .filter { it.recordType == FinancialRecordType.ACT }
            .mapNotNull { record ->
                val subproject = parentSubproject(record.projectId) ?: return@mapNotNull null
                val uah = when (record.currency.uppercase()) {
                    "UAH" -> record.amount
                    "EUR" -> record.eurExchangeRate?.takeIf { it > 0 }?.let { record.amount * it }
                    else -> null
                } ?: return@mapNotNull null
                subproject.id to uah
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sum() }
        data class Aggregate(var contract: Double = 0.0, var completed: Double = 0.0)
        fun aggregate(label: (oms.umitaf.domain.Project) -> String): List<FinancialProgressItemResponse> {
            val rows = linkedMapOf<String, Aggregate>()
            subprojects.forEach { subproject ->
                val key = label(subproject).trim().ifBlank { "Unspecified" }
                val totals = rows.getOrPut(key) { Aggregate() }
                totals.contract += (subproject.subprojectContractAmount ?: subproject.budgetPlanned).toDouble()
                totals.completed += completedBySubprojectId[subproject.id] ?: 0.0
            }
            return rows.map { (label, totals) ->
                FinancialProgressItemResponse(
                    label = label,
                    contractAmountUah = totals.contract,
                    completedAmountUah = totals.completed,
                    completionPct = if (totals.contract > 0) totals.completed * 100.0 / totals.contract else 0.0
                )
            }.sortedBy { it.label }
        }
        call.respond(
            FinancialProgressResponse(
                byRegion = aggregate { it.region.orEmpty() },
                byConstructionType = aggregate { it.constructionType.orEmpty() },
                bySector = aggregate { it.sector.orEmpty() }
            )
        )
    }

    get("/api/v1/financials") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val records = AppContainer.financialRecordService.getAll()
        // An empty registry needs no project lookup.  This is common in a new
        // deployment and avoids loading the full project tree just to return [].
        if (records.isEmpty()) {
            call.respond(emptyList<FinancialRecordListItemResponse>())
            return@get
        }
        val accessibleProjects = AppContainer.projectService.getAllProjects()
        val projectUuidsById = accessibleProjects.associate { it.id to it.uuid.toString() }
        call.respond(
            records.mapNotNull { record ->
                projectUuidsById[record.projectId]?.let { projectUuid ->
                    FinancialRecordListItemResponse(projectUuid, record.toResponse())
                }
            }
        )
    }

    /**
     * Builds the complete current registry view server-side. The browser sends
     * only its filter/sort state, never a pagination page or an unbounded list
     * of UUIDs, so a PDF remains complete even for large financial registers.
     */
    get("/api/v1/financials/export-table-pdf") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val language = if (call.request.queryParameters["lang"].equals("en", ignoreCase = true)) {
            FinancialTablePdfExportService.Language.EN
        } else FinancialTablePdfExportService.Language.UK
        val currency = call.request.queryParameters["currency"]?.uppercase().takeIf { it in setOf("EUR", "UAH") } ?: "EUR"
        val recordType = call.request.queryParameters["recordType"]?.takeIf { it in setOf("invoice", "act", "payment", "advance") }
        val paymentPurpose = call.request.queryParameters["paymentPurpose"]?.takeIf { it in setOf("works", "equipment", "technical_supervision", "engineer_consultant") }
        val tranche = call.request.queryParameters["tranche"]?.toIntOrNull()?.takeIf { it in setOf(1, 2) }
        val subprojectUuid = call.request.queryParameters["subproject"]?.trim()?.takeIf(String::isNotEmpty)
        val region = call.request.queryParameters["region"]?.trim()?.takeIf(String::isNotEmpty)
        val month = call.request.queryParameters["month"]?.takeIf { it.matches(Regex("\\d{4}-\\d{2}")) }
        val sort = call.request.queryParameters["sort"].orEmpty()
        val ascending = call.request.queryParameters["ascending"]?.toBooleanStrictOrNull() ?: false
        val projectsById = AppContainer.projectService.getAllProjects().associateBy { it.id }
        fun rowFor(record: oms.umitaf.domain.FinancialRecord): FinancialTablePdfExportService.Row? {
            val project = projectsById[record.projectId] ?: return null
            val ancestry = generateSequence(project) { current -> current.parentProjectId?.let(projectsById::get) }
                .toList().asReversed()
            val subproject = ancestry.firstOrNull { it.projectType == ProjectType.SUBPROJECT }
            val subprojectPart = ancestry.firstOrNull { it.projectType == ProjectType.SUBPROJECT_PART }
            return FinancialTablePdfExportService.Row(
                record = record,
                trancheNumber = subproject?.trancheNumber ?: project.trancheNumber,
                subprojectUuid = subproject?.uuid?.toString(),
                subprojectName = subproject?.name ?: project.name,
                subprojectCode = subproject?.siteNumber ?: project.siteNumber,
                subprojectPartName = subprojectPart?.name,
                region = subproject?.region ?: project.region.orEmpty()
            )
        }
        fun isRequestedTranche(value: Int) = when (tranche) {
            1 -> value == 1 || value == 8
            2 -> value == 2 || value == 9
            else -> true
        }
        fun displayedAmount(record: oms.umitaf.domain.FinancialRecord): Long? = when {
            record.currency.equals(currency, ignoreCase = true) -> (record.amount * 100).roundToLong()
            currency == "EUR" -> record.amountEurCents
            record.currency.equals("EUR", ignoreCase = true) && record.eurExchangeRate != null && record.eurExchangeRate > 0 && record.eurExchangeRate != 1.0 ->
                (record.amount * record.eurExchangeRate * 100).roundToLong()
            else -> null
        }
        val rows = AppContainer.financialRecordService.getAll().mapNotNull(::rowFor)
            .filter { row ->
                (recordType == null || row.record.recordType.name.equals(recordType, ignoreCase = true)) &&
                    (paymentPurpose == null || row.record.paymentPurpose == paymentPurpose) &&
                    isRequestedTranche(row.trancheNumber) &&
                    (subprojectUuid == null || row.subprojectUuid == subprojectUuid) &&
                    (region == null || sameRegion(region, row.region)) &&
                    (month == null || (row.record.paymentDate ?: row.record.recordDate).toString().take(7) == month)
            }
            .sortedByDescending { it.record.recordDate }
            .sortedWith(compareBy<FinancialTablePdfExportService.Row> {
                when (sort) {
                    "type" -> it.record.recordType.name
                    "purpose" -> it.record.paymentPurpose
                    "tranche" -> it.trancheNumber.toString()
                    "subproject" -> it.subprojectName
                    "subprojectCode" -> it.subprojectCode.orEmpty()
                    "subprojectPart" -> it.subprojectPartName.orEmpty()
                    "amount" -> (displayedAmount(it.record) ?: Long.MIN_VALUE).toString().padStart(20, '0')
                    "currency" -> currency
                    "description" -> it.record.description ?: it.record.milestone.orEmpty()
                    "author" -> ""
                    else -> it.record.recordDate.toString()
                }
            }.let { if (ascending) it else it.reversed() })
        val filterSummary = call.request.queryParameters["filters"]?.take(500)
        val pdf = withContext(Dispatchers.IO) {
            financialTablePdfExportService.export(rows, language, currency, filterSummary)
        }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=oms-financial-monitoring.pdf")
        call.respondBytes(pdf, ContentType.Application.Pdf)
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
            var imported: oms.umitaf.service.FinancialImportResult? = null

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

/** Matches the browser's canonical region filter without relying on translated display labels. */
private fun sameRegion(left: String, right: String?): Boolean =
    left.normalizedRegion() == right.orEmpty().normalizedRegion()

private fun String.normalizedRegion(): String =
    lowercase().replace(Regex("[.,]"), "").replace(Regex("\\s+(область|обл|oblast|region)$"), "").replace(Regex("\\s+"), " ").trim()
