package oms.urp3.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import oms.urp3.config.AppContainer
import oms.urp3.dto.*
import oms.urp3.domain.FinancialRecordType
import oms.urp3.domain.ProjectType
import oms.urp3.service.FinancialTablePdfExportService
import oms.urp3.service.PaymentMonitoringPdfExportService
import oms.urp3.service.GoogleDriveOAuth
import oms.urp3.service.paymentMonitoringContractType
import oms.urp3.service.paymentMonitoringSubprojectCode
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

@Serializable
private data class PaymentMonitoringGoogleRefreshRequest(val ticket: String)

fun Route.financialRoutes() {
    val financialTablePdfExportService = FinancialTablePdfExportService()
    val paymentMonitoringPdfExportService = PaymentMonitoringPdfExportService()
    get("/api/v1/financials/payment-monitoring") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        try {
            val snapshot = withContext(Dispatchers.IO) { AppContainer.paymentMonitoringService.current() }
            call.respond(
                PaymentMonitoringResponse(
                    refreshedAt = snapshot.refreshedAt,
                    items = snapshot.items.map { item ->
                        PaymentMonitoringItemResponse(
                            code = item.code,
                            totalInvestmentUah = item.totalInvestmentUah,
                            eibInvestmentUah = item.eibInvestmentUah,
                            localCoFinancingUah = item.localCoFinancingUah,
                            paidAndInProgressUah = item.paidAndInProgressUah,
                            paidUah = item.paidUah,
                            remainingUah = item.remainingUah,
                            plannedEibSharePct = item.plannedEibSharePct,
                            factualEibSharePct = item.factualEibSharePct,
                            eibShareCompliance = item.eibShareCompliance
                        )
                    },
                    transactions = snapshot.transactions.map { transaction ->
                        PaymentMonitoringTransactionResponse(
                            sourceReference = transaction.sourceReference,
                            code = transaction.code,
                            batchId = transaction.batchId,
                            paymentDate = transaction.paymentDate.toString(),
                            amountUah = transaction.amountUah,
                            amountEur = transaction.amountEur
                        )
                    }
                )
            )
        } catch (error: IllegalArgumentException) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to error.message.orEmpty()))
        }
    }

    get("/api/v1/financials/payment-monitoring/export-pdf") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val language = when (call.request.queryParameters["lang"]?.lowercase()) {
            "en" -> PaymentMonitoringPdfExportService.Language.EN
            else -> PaymentMonitoringPdfExportService.Language.UK
        }
        val snapshot = try {
            withContext(Dispatchers.IO) { AppContainer.paymentMonitoringService.current() }
        } catch (error: IllegalArgumentException) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to error.message.orEmpty()))
            return@get
        }
        val pdf = withContext(Dispatchers.IO) {
            paymentMonitoringPdfExportService.export(snapshot.items.sortedBy { it.code }, language)
        }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=oms-payment-monitoring.pdf")
        call.respondBytes(pdf, ContentType.Application.Pdf)
    }

    post("/api/v1/financials/payment-monitoring/refresh") {
        call.requireRole("ADMIN") ?: return@post
        call.respondPaymentMonitoringRefresh(HttpStatusCode.Accepted) {
            AppContainer.paymentMonitoringService.startApprovedSourceRefresh().toResponse()
        }
    }

    post("/api/v1/financials/payment-monitoring/refresh/google") {
        val user = call.requireRole("ADMIN") ?: return@post
        val request = call.receive<PaymentMonitoringGoogleRefreshRequest>()
        call.respondPaymentMonitoringRefresh(HttpStatusCode.Accepted) {
            require(call.request.headers["Origin"] == GoogleDriveOAuth.origin) { "Start the update on the OMS website." }
            val token = GoogleDriveOAuth.consume(user.userId, call.request.cookies[googleBrowserCookie].orEmpty(), request.ticket)
            AppContainer.paymentMonitoringService.startGoogleSourceRefresh(token).toResponse()
        }
    }

    post("/api/v1/financials/payment-monitoring/refresh/upload") {
        call.requireRole("ADMIN") ?: return@post
        var workbook: ByteArray? = null
        try {
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val fileName = part.originalFileName.orEmpty()
                    require(fileName.endsWith(".xlsx", true) || fileName.endsWith(".xls", true)) {
                        "Select an XLS or XLSX payment monitoring workbook."
                    }
                    val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                    require(bytes.size <= 20_000_000) { "The payment monitoring workbook must not exceed 20 MB." }
                    workbook = bytes
                }
                part.dispose()
            }
            call.respondPaymentMonitoringRefresh(HttpStatusCode.Accepted) {
                AppContainer.paymentMonitoringService.startWorkbookRefresh(
                    workbook ?: throw IllegalArgumentException("Multipart field 'file' is required.")
                ).toResponse()
            }
        } catch (error: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", error.message ?: "Invalid payment monitoring workbook."))
        }
    }

    get("/api/v1/financials/payment-monitoring/refresh/{id}") {
        call.requireRole("ADMIN") ?: return@get
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Refresh ID is required."))
        val status = AppContainer.paymentMonitoringService.getRefresh(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Payment-monitoring refresh not found."))
        call.respond(status.toResponse())
    }

    post("/api/v1/financials/payment-monitoring/refresh/{id}/cancel") {
        call.requireRole("ADMIN") ?: return@post
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Refresh ID is required."))
        val status = AppContainer.paymentMonitoringService.cancelRefresh(id)
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Payment-monitoring refresh not found."))
        call.respond(status.toResponse())
    }

    post("/api/v1/financials/payment-monitoring/import") {
        val session = call.requireRole("ADMIN") ?: return@post
        var workbook: ByteArray? = null
        try {
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val fileName = part.originalFileName.orEmpty()
                    require(fileName.endsWith(".xlsx", true) || fileName.endsWith(".xls", true)) {
                        "Select an XLS or XLSX payment monitoring workbook."
                    }
                    val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                    require(bytes.size <= 20_000_000) { "The payment monitoring workbook must not exceed 20 MB." }
                    workbook = bytes
                }
                part.dispose()
            }
            val snapshot = withContext(Dispatchers.IO) {
                AppContainer.paymentMonitoringService.importWorkbook(
                    workbook ?: throw IllegalArgumentException("Multipart field 'file' is required.")
                )
            }
            AppContainer.auditLogService.record(session.userId, "payment_monitoring_imported", "payment_monitoring", 0L)
            call.respond(PaymentMonitoringImportResponse(snapshot.items.size, snapshot.refreshedAt))
        } catch (error: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", error.message ?: "Invalid payment monitoring workbook."))
        }
    }

    get("/api/v1/financials/progress") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val requestedRegion = call.request.queryParameters["region"]
            ?.trim()
            ?.replace(Regex("\\s+(область|oblast|region)$", RegexOption.IGNORE_CASE), "")
            ?.takeIf(String::isNotBlank)
        val requestedTranche = call.request.queryParameters["tranche"]?.toIntOrNull()
        val requestedSubprojectUuid = call.request.queryParameters["subprojectUuid"]
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val requestedYear = call.request.queryParameters["year"]?.toIntOrNull()
            ?.takeIf { it in 2000..2100 }
        // Region/sector progress reads both the hierarchy and the complete
        // ledger.  It is an aggregate, not a latency-sensitive mutation, so
        // keep its synchronous repository work off the request dispatcher.
        val response = withContext(Dispatchers.IO) {
        val projects = AppContainer.projectService.getAllProjects()
        val byId = projects.associateBy { it.id }
        val subprojects = projects.filter { project ->
            project.projectType == ProjectType.SUBPROJECT &&
                (requestedRegion == null || project.region.orEmpty()
                    .replace(Regex("\\s+(область|oblast|region)$", RegexOption.IGNORE_CASE), "")
                    .equals(requestedRegion, ignoreCase = true)) &&
                (requestedTranche == null || project.trancheNumber.matchesFinancialTranche(requestedTranche)) &&
                (requestedSubprojectUuid == null || project.uuid.toString() == requestedSubprojectUuid)
        }
        // Legacy lot records may use CODE#LotN while payments belong to the
        // base subproject. Build this lookup once: resolving each payment by
        // repeatedly scanning all subprojects becomes expensive as both the
        // financial registry and the subproject register grow.
        val subprojectByBaseCode = linkedMapOf<String, oms.urp3.domain.Project>()
        subprojects.forEach { subproject ->
            subprojectByBaseCode.putIfAbsent(
                subproject.siteNumber.substringBefore('#').trim().uppercase(),
                subproject
            )
        }
        fun parentSubproject(projectId: Long): oms.urp3.domain.Project? {
            var current = byId[projectId] ?: return null
            while (current.projectType != ProjectType.SUBPROJECT) {
                current = current.parentProjectId?.let(byId::get) ?: return null
            }
            // Early payment imports could create a temporary subproject with
            // a lot code (for example KH08_11#Lot1) before the real part was
            // available. Attribute that legacy row to its base subproject so
            // its region, sector and approved/contract amount are retained.
            val baseCode = current.siteNumber.substringBefore('#').trim()
            return if (baseCode.equals(current.siteNumber.trim(), ignoreCase = true)) current else
                subprojectByBaseCode[baseCode.uppercase()] ?: current
        }
        // Financial execution is cash disbursed for construction works against
        // the construction-contract/approved-work budget.  ACT records are
        // evidence of delivery rather than a payment, and TS/CSC/equipment
        // payments have different financing bases.  Mixing either of them
        // into this numerator previously made sector progress exceed 100%.
        val sourceTransactions = AppContainer.paymentMonitoringService.currentIfAvailable()?.transactions.orEmpty()
        val disbursedBySubprojectId = if (sourceTransactions.isNotEmpty()) {
            // The approved workbook is a payment source.  Its W suffix marks
            // construction works; TS/CSC rows belong to different charts and
            // must not inflate construction financial progress.
            sourceTransactions.asSequence()
                .filter { it.code.paymentMonitoringContractType() == "W" }
                .filter { requestedYear == null || it.paymentDate.year == requestedYear }
                .mapNotNull { payment ->
                    val baseCode = payment.code.paymentMonitoringSubprojectCode().uppercase()
                    subprojectByBaseCode[baseCode]?.id?.let { it to payment.amountUah }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, amounts) -> amounts.sum() }
        } else AppContainer.financialRecordService.getAll()
            .asSequence()
            .filter { it.recordType == FinancialRecordType.PAYMENT || it.recordType == FinancialRecordType.ADVANCE }
            .filter { it.paymentPurpose.equals("works", ignoreCase = true) }
            .filter { requestedYear == null || (it.paymentDate ?: it.recordDate).year == requestedYear }
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
        fun aggregate(label: (oms.urp3.domain.Project) -> String, canonical: (String) -> String): List<FinancialProgressItemResponse> {
            val rows = linkedMapOf<String, Aggregate>()
            subprojects.forEach { subproject ->
                val key = canonical(label(subproject).trim().ifBlank { "Unspecified" })
                val totals = rows.getOrPut(key) { Aggregate() }
                // A source may explicitly store a zero/blank construction
                // contract while payments have already been imported.  Zero
                // must not become the denominator in that case: use the
                // approved subproject funding as the documented fallback.
                val executionBase = subproject.subprojectContractAmount
                    ?.takeIf { it > 0L }
                    ?: subproject.budgetPlanned.takeIf { it > 0L }
                    ?: 0L
                totals.contract += executionBase.toDouble()
                totals.completed += disbursedBySubprojectId[subproject.id] ?: 0.0
            }
            return rows.map { (label, totals) ->
                FinancialProgressItemResponse(
                    label = label,
                    contractAmountUah = totals.contract,
                    completedAmountUah = totals.completed,
                    completionPct = financialProgressPercentage(totals.completed, totals.contract)
                )
            }.sortedBy { it.label }
        }
        FinancialProgressResponse(
                byRegion = aggregate({ it.region.orEmpty() }, ::canonicalFinancialRegion),
                byConstructionType = aggregate({ it.constructionType.orEmpty() }, ::canonicalFinancialConstructionType),
                bySector = aggregate({ it.sector.orEmpty() }, ::canonicalFinancialSector)
        )
        }
        call.respond(response)
    }

    get("/api/v1/financials") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val response = withContext(Dispatchers.IO) {
            val records = AppContainer.financialRecordService.getAll()
            // An empty registry needs no project lookup.  This is common in a
            // new deployment and avoids loading the full project tree just to
            // return an empty result.
            if (records.isEmpty()) {
                emptyList<FinancialRecordListItemResponse>()
            } else {
                val accessibleProjects = AppContainer.projectService.getAllProjects()
                val projectUuidsById = accessibleProjects.associate { it.id to it.uuid.toString() }
                records.mapNotNull { record ->
                    projectUuidsById[record.projectId]?.let { projectUuid ->
                        FinancialRecordListItemResponse(projectUuid, record.toResponse())
                    }
                }
            }
        }
        call.respond(response)
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
        val year = call.request.queryParameters["year"]?.toIntOrNull()?.takeIf { it in 2000..2100 }
        val sort = call.request.queryParameters["sort"].orEmpty()
        val ascending = call.request.queryParameters["ascending"]?.toBooleanStrictOrNull() ?: false
        val pdf = withContext(Dispatchers.IO) {
        val projectsById = AppContainer.projectService.getAllProjects().associateBy { it.id }
        fun rowFor(record: oms.urp3.domain.FinancialRecord): FinancialTablePdfExportService.Row? {
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
        fun displayedAmount(record: oms.urp3.domain.FinancialRecord): Long? = when {
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
                    (month == null || (row.record.paymentDate ?: row.record.recordDate).toString().take(7) == month) &&
                    (year == null || (row.record.paymentDate ?: row.record.recordDate).year == year)
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
            var imported: oms.urp3.service.FinancialImportResult? = null

            try {
                call.receiveMultipart().forEachPart { part ->
                    if (part is PartData.FileItem && part.name == "file") {
                        require((part.originalFileName ?: "").substringAfterLast('.', "").lowercase() in setOf("xls", "xlsx")) { "Only XLS or XLSX files are supported." }
                        val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                        require(bytes.size <= 20_000_000) { "File size must not exceed 20 MB." }
                        imported = withContext(Dispatchers.IO) {
                            AppContainer.financialRecordService.importWorkbook(
                                project.id,
                                java.io.ByteArrayInputStream(bytes)
                            )
                        }
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

/** Source workbooks may contain a technical code or `UA / EN` display value.
 * Aggregate them under one stable key before producing chart rows. */
internal fun canonicalFinancialSector(value: String): String = when (value.substringBefore('/').trim().lowercase()) {
    "education", "освіта" -> "education"
    "health", "healthcare", "охорона здоров'я", "охорона здоров’я" -> "healthcare"
    "housing", "житло" -> "housing"
    "water_supply", "water supply", "водопостачання" -> "water_supply"
    "sewerage", "водовідведення" -> "sewerage"
    "heat_supply", "heat supply", "теплопостачання" -> "heat_supply"
    "shelter", "укриття" -> "shelter"
    "unspecified", "не визначено", "не вказано", "" -> "Unspecified"
    else -> value.substringBefore('/').trim()
}

internal fun canonicalFinancialConstructionType(value: String): String = when (value.substringBefore('/').trim().lowercase()) {
    "new_construction", "new construction", "нове будівництво" -> "new_construction"
    "reconstruction", "реконструкція" -> "reconstruction"
    "renovation", "капітальний ремонт" -> "renovation"
    "repair", "поточний ремонт" -> "repair"
    "unspecified", "не визначено", "не вказано", "" -> "Unspecified"
    else -> value.substringBefore('/').trim()
}

internal fun canonicalFinancialRegion(value: String): String = value
    .replace(Regex("\\s+(область|oblast|region)$", RegexOption.IGNORE_CASE), "")
    .trim()
    .ifBlank { "Unspecified" }

/** The ratio is calculated from aggregated amounts, never averaged from per-SP percentages. */
internal fun financialProgressPercentage(completedAmount: Double, contractAmount: Double): Double =
    if (contractAmount > 0.0) completedAmount * 100.0 / contractAmount else 0.0

private fun oms.urp3.service.PaymentMonitoringService.RefreshStatus.toResponse() =
    PaymentMonitoringRefreshResponse(id, status, progress, phase, importedCount, error)

private suspend fun io.ktor.server.application.ApplicationCall.respondPaymentMonitoringRefresh(
    success: HttpStatusCode,
    block: () -> PaymentMonitoringRefreshResponse
) {
    try {
        respond(success, block())
    } catch (exception: IllegalStateException) {
        respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", exception.message ?: "Payment-monitoring refresh is already running."))
    } catch (exception: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid payment monitoring data."))
    }
}

/** DB imports use both 1/8 for tranche A and 2/9 for tranche B. */
private fun Int.matchesFinancialTranche(filter: Int): Boolean = when (filter) {
    1 -> this == 1 || this == 8
    2 -> this == 2 || this == 9
    else -> this == filter
}

private suspend fun io.ktor.server.application.ApplicationCall.project() = parameters["projectUuid"]?.let { AppContainer.projectService.getProjectByUuid(it) } ?: run { financeNotFound("Project not found."); null }
private suspend fun io.ktor.server.application.ApplicationCall.financeError(e: IllegalArgumentException) { respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", e.message ?: "Invalid request.")) }
private suspend fun io.ktor.server.application.ApplicationCall.financeNotFound(message: String) { respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", message)) }

/** Matches the browser's canonical region filter without relying on translated display labels. */
private fun sameRegion(left: String, right: String?): Boolean =
    left.normalizedRegion() == right.orEmpty().normalizedRegion()

private fun String.normalizedRegion(): String =
    lowercase().replace(Regex("[.,]"), "").replace(Regex("\\s+(область|обл|oblast|region)$"), "").replace(Regex("\\s+"), " ").trim()
