package oms.urp3.api

import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import oms.urp3.config.AppContainer
import oms.urp3.dto.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oms.urp3.domain.ProjectType
import oms.urp3.domain.FinancialRecordType
import oms.urp3.domain.ProcurementRecord
import oms.urp3.service.ProjectPdfExportService
import oms.urp3.service.SubprojectsTablePdfExportService
import kotlin.math.roundToLong
import java.math.BigDecimal
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import java.nio.file.Files

@Serializable
private data class SubprojectRegistryRefreshResponse(
    val id: String,
    val status: String,
    val progress: Int,
    val phase: String,
    val importedCount: Int? = null,
    val error: String? = null
)

/** Ordered client-side table selection used by the printable subproject registry. */
@Serializable
private data class SubprojectsTablePdfRequest(
    val projectUuids: List<String> = emptyList(),
    val language: String = "uk",
    val filterSummary: String? = null
)

private fun oms.urp3.service.SubprojectRegistryRefreshService.RefreshStatus.toResponse() =
    SubprojectRegistryRefreshResponse(id, status, progress, phase, importedCount, error)

/**
 * Returns the persisted EUR amount when available and reconstructs it for
 * legacy records that were saved before EUR cents became mandatory.
 *
 * A UAH value is converted only when that record carries the exchange rate
 * used at the time of saving. Guessing a current rate here would make map
 * financial indicators historically inaccurate.
 */
private fun oms.urp3.domain.FinancialRecord.disbursedEurCents(): Long? =
    amountEurCents ?: when (currency.trim().uppercase()) {
        "EUR" -> (amount * 100).roundToLong()
        "UAH" -> eurExchangeRate
            ?.takeIf { it > 0.0 }
            ?.let { rate -> (amount / rate * 100).roundToLong() }
        else -> null
    }

/** Returns the stored UAH amount without applying a current-day exchange rate. */
private fun oms.urp3.domain.FinancialRecord.disbursedUah(): BigDecimal? = when (currency.trim().uppercase()) {
    "UAH" -> BigDecimal.valueOf(amount)
    "EUR" -> eurExchangeRate?.takeIf { it > 0.0 }?.let { BigDecimal.valueOf(amount * it) }
    else -> null
}

/**
 * Маршрути роботи з проєктами.
 */
fun Route.projectRoutes() {

    val projectService =
        AppContainer.projectService
    val projectPdfExportService = ProjectPdfExportService()
    val subprojectsTablePdfExportService = SubprojectsTablePdfExportService()

    // The monitoring workbook is maintained outside OMS.  Its upload is
    // intentionally administrator-only and starts a background job so the
    // browser remains responsive while a full registry snapshot is parsed.
    post("/api/v1/projects/registry/refresh/upload") {
        call.requireRole("ADMIN") ?: return@post
        var workbook: ByteArray? = null
        try {
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    val name = part.originalFileName.orEmpty()
                    require(name.endsWith(".xlsx", true) || name.endsWith(".xls", true)) {
                        "Select an XLS or XLSX subproject monitoring workbook."
                    }
                    val bytes = part.provider().readRemaining(20_000_001).readByteArray()
                    require(bytes.size <= 20_000_000) { "The subproject monitoring workbook must not exceed 20 MB." }
                    workbook = bytes
                }
                part.dispose()
            }
            call.respond(
                HttpStatusCode.Accepted,
                AppContainer.subprojectRegistryRefreshService.startWorkbookRefresh(
                    workbook ?: throw IllegalArgumentException("Multipart field 'file' is required.")
                ).toResponse()
            )
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid subproject monitoring workbook."))
        } catch (exception: IllegalStateException) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", exception.message ?: "A subproject registry refresh is already running."))
        }
    }

    get("/api/v1/projects/registry/refresh/{id}") {
        call.requireRole("ADMIN") ?: return@get
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Refresh ID is required."))
        val status = AppContainer.subprojectRegistryRefreshService.refresh(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Subproject registry refresh not found."))
        call.respond(status.toResponse())
    }

    post("/api/v1/projects/registry/refresh/{id}/cancel") {
        call.requireRole("ADMIN") ?: return@post
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Refresh ID is required."))
        val status = AppContainer.subprojectRegistryRefreshService.cancel(id)
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Subproject registry refresh not found."))
        call.respond(status.toResponse())
    }

    get("/api/v1/exchange-rates/eur") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val requestedDate = try {
            call.request.queryParameters["date"]?.let(java.time.LocalDate::parse)
                ?: java.time.LocalDate.now(java.time.ZoneId.of("Europe/Kyiv"))
        } catch (_: java.time.format.DateTimeParseException) {
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Use a valid date in YYYY-MM-DD format."))
        }
        try {
            val (date, rate) = withContext(Dispatchers.IO) {
                AppContainer.nbuExchangeRateService.eurRate(requestedDate)
            }
            call.respond(ProjectExchangeRateResponse(java.math.BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString(), date.toString()))
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("EXCHANGE_RATE_UNAVAILABLE", "NBU rate is unavailable. Retry or enter a rate manually."))
        }
    }

    post("/api/v1/geocode/address") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
        val request = call.receive<GeocodeAddressRequest>()
        try {
            val result = withContext(Dispatchers.IO) {
                AppContainer.geocodingService.geocode(request.address, request.city, request.region)
            }
            if (result == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Address was not found."))
            else call.respond(result)
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid address."))
        } catch (exception: Exception) {
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("GEOCODING_ERROR", "Unable to resolve the address."))
        }
    }

    /**
     * Returns a filtered page of the project hierarchy together with stable
     * pagination metadata. The client may request up to 500 rows per page for
     * its shared navigation snapshot, while table screens retain their own UI
     * pagination without repeatedly rebuilding this response.
     */
    get("/api/v1/projects") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
        if (page < 1 || pageSize !in 1..500) {
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "page must be positive and pageSize must be between 1 and 500."))
        }
        // This shared registry enriches every row with hierarchy, monitoring,
        // and payment data.  All three repositories use synchronous JDBC, so
        // calculate it on the IO dispatcher rather than occupying Ktor's
        // request dispatcher while the initial workspace snapshot is loading.
        val response = withContext(Dispatchers.IO) {
            val projects = projectService.searchProjects(
                status = call.request.queryParameters["status"],
                region = call.request.queryParameters["region"],
                search = call.request.queryParameters["search"]
            )
            val total = projects.size.toLong()
            // Keep an out-of-range page a normal empty result. Calculating
            // the offset as Int could overflow for a malformed very large
            // page query and turn a harmless request into a 500 response.
            val offset = (page.toLong() - 1L) * pageSize.toLong()
            val pagedProjects = if (offset >= projects.size) emptyList() else
                projects.drop(offset.toInt()).take(pageSize)

        // The response may be a filtered/paginated slice, but resolving a
        // payment on a subproject part needs its parent outside that slice.
        val hierarchy = projectService.getAllProjects()
        val projectsById = hierarchy.associateBy { it.id }
        val projectUuidById = projectsById.mapValues { (_, project) -> project.uuid.toString() }
        val monitoringByProjectId = projectService.monitoringDetailsByProjectIds(pagedProjects.map { it.id })
        // Build this once for the page, rather than performing a financial
        // query per map marker/project card. Payment and advance records are
        // actual disbursements; ACT rows describe completed work, not cash paid.
        fun owningSubprojectId(projectId: Long): Long? {
            var current = projectsById[projectId] ?: return null
            while (current.projectType != ProjectType.SUBPROJECT) {
                current = current.parentProjectId?.let(projectsById::get) ?: return null
            }
            return current.id
        }
        val disbursements = AppContainer.financialRecordService.getAll()
            .asSequence()
            .filter { it.recordType == FinancialRecordType.PAYMENT || it.recordType == FinancialRecordType.ADVANCE }
            .toList()
        val disbursedEurCentsBySubprojectId = disbursements
            .asSequence()
            .mapNotNull { record ->
                val subprojectId = owningSubprojectId(record.projectId) ?: return@mapNotNull null
                record.disbursedEurCents()?.let { subprojectId to it }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, cents) -> cents.sum() }
        val disbursedEurCentsByProjectId = disbursements
            .mapNotNull { record -> record.disbursedEurCents()?.let { record.projectId to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, cents) -> cents.sum() }
        val paidConstructionUahBySubprojectId = disbursements
            .asSequence()
            .filter { it.paymentPurpose.equals("works", ignoreCase = true) }
            .mapNotNull { record ->
                val subprojectId = owningSubprojectId(record.projectId) ?: return@mapNotNull null
                record.disbursedUah()?.let { subprojectId to it }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO) { total, amount -> total.add(amount) } }
        val paidConstructionUahByProjectId = disbursements
            .asSequence()
            .filter { it.paymentPurpose.equals("works", ignoreCase = true) }
            .mapNotNull { record -> record.disbursedUah()?.let { record.projectId to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO) { total, amount -> total.add(amount) } }
            ProjectListResponse(

                data =
                    pagedProjects.map { project ->

                        project.toResponse(
                            parentProjectUuid = projectUuidById[project.parentProjectId],
                            monitoring = monitoringByProjectId[project.id],
                            financingDisbursedEurCents = when (project.projectType) {
                                ProjectType.SUBPROJECT -> disbursedEurCentsBySubprojectId[project.id] ?: 0L
                                ProjectType.SUBPROJECT_PART -> disbursedEurCentsByProjectId[project.id] ?: 0L
                                else -> 0L
                            },
                            financingDisbursedUah = when (project.projectType) {
                                ProjectType.SUBPROJECT -> paidConstructionUahBySubprojectId[project.id] ?: BigDecimal.ZERO
                                ProjectType.SUBPROJECT_PART -> paidConstructionUahByProjectId[project.id] ?: BigDecimal.ZERO
                                else -> BigDecimal.ZERO
                            }
                        )
                    },

                meta = PageMetadata(

                    page = page,

                    perPage = pageSize,

                    total =
                        total,

                    totalPages =
                        if (projects.isEmpty()) {
                            0
                        } else {
                            ((total + pageSize - 1) / pageSize).toInt()
                        }
                )
            )
        }

        call.respond(response)
    }

    /**
     * Створює новий проєкт.
     */
    post("/api/v1/projects") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post

        val request = call.receive<CreateProjectRequest>()
        try {

            val result = withContext(Dispatchers.IO) {
                transaction {
            val project =
                projectService.createProject(

                    name = request.name,

                    address = request.address,

                    region = request.region,

                    city = request.city,

                    sector = request.sector,

                    constructionType =
                        request.constructionType,

                    budgetPlanned =
                        request.budgetPlanned,

                    siteName = request.siteName,

                    siteNumber = request.siteNumber,

                    latitude = request.latitude,

                    longitude = request.longitude,

                    engineerConsultantContractAmount = request.engineerConsultantContractAmount,

                    technicalSupervisionAmount = request.technicalSupervisionAmount,

                    projectType = request.projectType,

                    parentProjectUuid = request.parentProjectUuid,

                    trancheNumber = request.trancheNumber,

                    subprojectContractAmount = request.subprojectContractAmount,

                    startDate = request.startDate,

                    contractSignedDate = request.contractSignedDate,

                    plannedEndDate = request.plannedEndDate,

                    managerId = session.userId,
                )

            projectService.updateProject(
                project.uuid.toString(),
                UpdateProjectRequest(
                    description = request.description,
                    endDate = request.endDate,
                    constructionContractSigningDate = request.constructionContractSigningDate,
                    constructionStartDate = request.constructionStartDate,
                    projectedCompletionTime = request.projectedCompletionTime,
                    currency = request.currency,
                    contractorName = request.contractorName,
                    constructionContractNumber = request.constructionContractNumber,
                    technicalSupervisionName = request.technicalSupervisionName,
                    technicalSupervisionContractNumber = request.technicalSupervisionContractNumber,
                    technicalSupervisionContractDate = request.technicalSupervisionContractDate,
                    technicalSupervisionStartDate = request.technicalSupervisionStartDate,
                    technicalSupervisionPlannedEndDate = request.technicalSupervisionPlannedEndDate,
                    engineerConsultantName = request.engineerConsultantName,
                    engineerConsultantContractNumber = request.engineerConsultantContractNumber,
                    engineerConsultantContractDate = request.engineerConsultantContractDate,
                    engineerConsultantStartDate = request.engineerConsultantStartDate,
                    engineerConsultantPlannedEndDate = request.engineerConsultantPlannedEndDate,
                    amounts = request.amounts,
                    status = request.status
                )
            ) ?: project
                }
            }

            AppContainer.auditLogService.record(session.userId, "project_created", result.projectType.name.lowercase(), result.id)
            call.respond(
                HttpStatusCode.Created,
                result.toResponse()
            )

        } catch (exception: IllegalArgumentException) {

            call.respond(
                HttpStatusCode.BadRequest,

                ErrorResponse(
                    error = "VALIDATION_ERROR",

                    message =
                        exception.message
                            ?: "Помилка створення проєкту."
                )
            )
        }
    }

    /**
     * Повертає детальну інформацію
     * про проєкт.
     */
    get("/api/v1/projects/{uuid}") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get

        val uuid =
            call.parameters["uuid"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,

                    ErrorResponse(
                        error = "VALIDATION_ERROR",
                        message = "UUID проєкту відсутній."
                    )
                )

        val project =
            projectService.getProjectByUuid(uuid)

        if (project == null) {

            call.respond(
                HttpStatusCode.NotFound,

                ErrorResponse(
                    error = "NOT_FOUND",
                    message = "Проєкт не знайдено."
                )
            )

            return@get
        }
        if (!call.requireProjectAccess(session, uuid)) return@get

        val response = withContext(Dispatchers.IO) {
            val monitoring = AppContainer.projectRepository.monitoringDetails(project.id)
            val contractorNameEn = monitoring?.sourceSubprojectId?.let { sourceId ->
                AppContainer.procurementService.getForProjectCard(sourceId, null)
                    .firstOrNull { !it.contractorNameEng.isNullOrBlank() }
                    ?.contractorNameEng
            }

            ProjectDetailsResponse(

                data =
                    project.toResponse(monitoring = monitoring),

                financialSummary = AppContainer.financialRecordService.summary(project).let {
                    FinancialSummaryResponse(
                        it.budgetPlanned,
                        it.constructionContractAmount,
                        it.amountSpent,
                        it.financialDocumentsAmount,
                        it.budgetRemaining,
                        it.completionPct,
                        it.financialCompletionPct
                    )
                },
                monitoringDetails = monitoring?.let {
                    // Source identifiers remain server-side; the UI needs only display values.
                    ProjectMonitoringDetailsResponse(
                        nameEn = it.nameEn,
                        municipalityNameUk = it.municipalityNameUk,
                        municipalityNameEn = it.municipalityNameEn,
                        settlementNameEn = it.settlementNameEn,
                        beneficiaryNameUk = it.beneficiaryNameUk,
                        beneficiaryNameEn = it.beneficiaryNameEn,
                        constructionProcurementStatus = it.constructionProcurementStatus,
                        constructionWorkStatus = it.constructionWorkStatus,
                        dreamProjectUrl = it.dreamProjectUrl,
                        dedUrl = it.dedUrl,
                        estimateUrl = it.estimateUrl,
                        expertiseUrl = it.expertiseUrl,
                        permitUrl = it.permitUrl,
                        constructionActNumber = it.constructionActNumber,
                        constructionActDate = it.constructionActDate,
                        completedWorkPct = it.completedWorkPct,
                        financingUsedPct = it.financingUsedPct,
                        paidFinancingPct = it.paidFinancingPct,
                        constructionWorkSummary = it.constructionWorkSummary,
                        constructionWorkComments = it.constructionWorkComments
                    )
                },
                contractorNameEn = contractorNameEn
            )
        }
        call.respond(response)
    }

    patch("/api/v1/projects/bulk-status") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@patch
        try {
            val request = call.receive<BulkProjectUpdateRequest>()
            val updated = projectService.bulkUpdateStatus(request.projectUuids, request.status)
            AppContainer.auditLogService.record(session.userId, "projects_bulk_status_updated", "project", 0)
            call.respond(BulkProjectUpdateResponse(updated))
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid bulk update."))
        }
    }

    patch("/api/v1/projects/bulk-reassign") {
        val session = call.requireRole("ADMIN") ?: return@patch
        try {
            val request = call.receive<BulkProjectReassignRequest>()
            val manager = AppContainer.userService.findById(request.managerId)
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project manager was not found."))
            require(manager.role.code.equals("PROJECT_MANAGER", ignoreCase = true) && manager.status.equals("active", ignoreCase = true)) {
                "Assignee must be an active Project Manager."
            }
            val updated = projectService.bulkReassign(request.projectUuids, request.managerId)
            AppContainer.auditLogService.record(session.userId, "projects_bulk_reassigned", "project", 0)
            call.respond(BulkProjectUpdateResponse(updated))
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid bulk reassignment."))
        }
    }

    get("/api/v1/projects/export") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        val requestedUuids = call.request.queryParameters.getAll("uuid")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        val projects = projectService.getAllProjects().filter { project ->
            requestedUuids.isEmpty() || project.uuid.toString() in requestedUuids
        }
        val monitoringByProjectId = projectService.monitoringDetailsByProjectIds(projects.map { it.id })
        // The procurement register can contain several contracts for one
        // subproject (Works, TS and CSC). Load it once and aggregate only
        // actually signed contracts for the Subprojects export sheet.
        val signedProcurementsByProjectId = AppContainer.procurementService.getAll()
            .asSequence()
            .filter { procurement -> procurement.isSignedContract() }
            .groupBy { procurement -> procurement.projectId }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=oms-projects.xlsx")
        call.respondOutputStream(ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("projects")
                val headers = listOf("uuid", "name", "project_type", "parent_project_id", "status", "region", "city", "budget_planned", "currency")
                sheet.createRow(0).apply { headers.forEachIndexed { index, header -> createCell(index).setCellValue(header) } }
                projects.forEachIndexed { index, project ->
                    sheet.createRow(index + 1).apply {
                        createCell(0).setCellValue(project.uuid.toString())
                        createCell(1).setCellValue(project.name)
                        createCell(2).setCellValue(project.projectType.name.lowercase())
                        createCell(3).setCellValue(project.parentProjectId?.toString().orEmpty())
                        createCell(4).setCellValue(project.status.name.lowercase())
                        createCell(5).setCellValue(project.region.orEmpty())
                        createCell(6).setCellValue(project.city.orEmpty())
                        createCell(7).setCellValue(project.budgetPlanned.toDouble())
                        createCell(8).setCellValue(project.currency)
                    }
                }
                headers.indices.forEach(sheet::autoSizeColumn)

                // Keep the legacy sheet intact and add a purpose-built SP tab.
                // It is intentionally one row per subproject, so recipients can
                // compare monitoring and signed-contract financing without
                // manually joining the projects and procurement workbooks.
                val subprojectsSheet = workbook.createSheet("Subprojects")
                val subprojectHeaders = listOf(
                    "subproject_code", "subproject_name", "tranche", "region",
                    "municipality_uk", "municipality_en", "final_beneficiary_uk", "final_beneficiary_en",
                    "works_contract_amount_uah", "works_eib_financing_uah", "works_local_financing_uah", "works_local_cofinancing_pct",
                    "ts_contract_amount_uah", "ts_eib_financing_uah", "ts_local_financing_uah", "ts_local_cofinancing_pct",
                    "csc_contract_amount_uah", "csc_eib_financing_uah", "csc_local_financing_uah", "csc_local_cofinancing_pct"
                )
                subprojectsSheet.createRow(0).apply {
                    subprojectHeaders.forEachIndexed { index, header -> createCell(index).setCellValue(header) }
                }
                projects.asSequence()
                    .filter { it.projectType == ProjectType.SUBPROJECT }
                    .forEachIndexed { index, subproject ->
                        val monitoring = monitoringByProjectId[subproject.id]
                        val contracts = signedProcurementsByProjectId[subproject.id].orEmpty()
                        val works = contracts.filter { it.isWorksContract() }.signedContractTotals()
                        val technicalSupervision = contracts.filter { it.isTechnicalSupervisionContract() }.signedContractTotals()
                        val engineerConsultant = contracts.filter { it.isEngineerConsultantContract() }.signedContractTotals()
                        subprojectsSheet.createRow(index + 1).apply {
                            createCell(0).setCellValue(subproject.siteNumber)
                            createCell(1).setCellValue(subproject.name)
                            createCell(2).setCellValue(subproject.trancheNumber.toString())
                            createCell(3).setCellValue(subproject.region.orEmpty())
                            createCell(4).setCellValue(monitoring?.municipalityNameUk.orEmpty())
                            createCell(5).setCellValue(monitoring?.municipalityNameEn.orEmpty())
                            createCell(6).setCellValue(monitoring?.beneficiaryNameUk.orEmpty())
                            createCell(7).setCellValue(monitoring?.beneficiaryNameEn.orEmpty())
                            setNumeric(8, works.contractAmountUah)
                            setNumeric(9, works.eibFinancingUah)
                            setNumeric(10, works.localFinancingUah)
                            setNumeric(11, works.localCofinancingPct)
                            setNumeric(12, technicalSupervision.contractAmountUah)
                            setNumeric(13, technicalSupervision.eibFinancingUah)
                            setNumeric(14, technicalSupervision.localFinancingUah)
                            setNumeric(15, technicalSupervision.localCofinancingPct)
                            setNumeric(16, engineerConsultant.contractAmountUah)
                            setNumeric(17, engineerConsultant.eibFinancingUah)
                            setNumeric(18, engineerConsultant.localFinancingUah)
                            setNumeric(19, engineerConsultant.localCofinancingPct)
                        }
                    }
                subprojectsSheet.createFreezePane(0, 1)
                subprojectHeaders.indices.forEach(subprojectsSheet::autoSizeColumn)
                workbook.write(this)
            }
        }
    }

    /**
     * Produces one printable registry table from the UUIDs already filtered and
     * sorted in the browser. Keeping that ordered selection avoids reapplying
     * UI-only search and tree rules on the server or losing rows to pagination.
     */
    suspend fun exportSubprojectsTablePdf(
        targetCall: ApplicationCall,
        requestedUuids: List<String>,
        requestedLanguage: String,
        requestedFilterSummary: String?
    ) {
        val allProjects = projectService.getAllProjects()
        val projectsByUuid = allProjects.associateBy { it.uuid.toString() }
        val projects = if (requestedUuids.isEmpty()) allProjects else requestedUuids.mapNotNull(projectsByUuid::get)
        val language = if (requestedLanguage.equals("en", ignoreCase = true)) {
            SubprojectsTablePdfExportService.Language.EN
        } else SubprojectsTablePdfExportService.Language.UK
        val filterSummary = requestedFilterSummary?.take(500)
        val monitoring = projectService.monitoringDetailsByProjectIds(projects.map { it.id })
        targetCall.response.header(HttpHeaders.ContentDisposition, "attachment; filename=oms-subprojects.pdf")
        targetCall.respondOutputStream(ContentType.Application.Pdf) {
            write(subprojectsTablePdfExportService.export(projects, monitoring, language, filterSummary))
        }
    }

    // Keep the legacy GET endpoint for previously deployed browser bundles.
    get("/api/v1/projects/export-table-pdf") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        exportSubprojectsTablePdf(
            targetCall = call,
            requestedUuids = call.request.queryParameters.getAll("uuid")
                ?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
            requestedLanguage = call.request.queryParameters["lang"].orEmpty(),
            requestedFilterSummary = call.request.queryParameters["filters"]
        )
    }

    post("/api/v1/projects/export-table-pdf") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
        val request = call.receive<SubprojectsTablePdfRequest>()
        exportSubprojectsTablePdf(
            targetCall = call,
            requestedUuids = request.projectUuids.map(String::trim).filter(String::isNotEmpty).distinct(),
            requestedLanguage = request.language,
            requestedFilterSummary = request.filterSummary
        )
    }

    patch("/api/v1/projects/{uuid}") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@patch
        val uuid = call.parameters["uuid"] ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        if (!call.requireProjectAccess(session, uuid)) return@patch
        try {
            val project = projectService.updateProject(uuid, call.receive<UpdateProjectRequest>())
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
            call.respond(project.toResponse())
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid project data."))
        }
    }

    post("/api/v1/projects/{uuid}/archive") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
        val uuid = call.parameters["uuid"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        val project = projectService.getProjectByUuid(uuid) ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        if (!call.requireProjectAccess(session, uuid)) return@post
        if (!projectService.archiveProject(uuid, session.userId)) return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        AppContainer.auditLogService.record(session.userId, "project_archived", project.projectType.name.lowercase(), project.id, oldValues = "{\"name\":\"${project.name}\"}")
        call.respond(HttpStatusCode.NoContent)
    }

    /** A self-contained brief for a subproject's general and financial tabs. */
    get("/api/v1/projects/{uuid}/export-pdf") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val uuid = call.parameters["uuid"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        val project = projectService.getProjectByUuid(uuid)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        if (!call.requireProjectAccess(session, uuid)) return@get
        if (project.projectType != ProjectType.SUBPROJECT) {
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "PDF export is available for subprojects only."))
        }
        val language = when (call.request.queryParameters["lang"]?.lowercase()) {
            "en" -> ProjectPdfExportService.Language.EN
            else -> ProjectPdfExportService.Language.UK
        }
        val safeCode = project.siteNumber.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "subproject" }
        val bytes = withContext(Dispatchers.IO) {
            val inspectionReports = AppContainer.inspectionReportService.getProjectReports(project.id)
            val subprojectCode = project.siteNumber.substringBefore('#').trim()
            val procurementRecords = AppContainer.procurementService.getAll().filter { record ->
                record.subProjectId.substringBefore('#').trim().equals(subprojectCode, ignoreCase = true) ||
                    record.subProjectLotId?.substringBefore('#')?.trim()?.equals(subprojectCode, ignoreCase = true) == true
            }
            val inspectionPhotos = inspectionReports.flatMap { report ->
                // Older imported SIR workbooks can contain images only in their
                // Photo Attachment sheet.  The report screen hydrates those
                // asynchronously, but an explicit PDF export must include
                // them on its first run as well.
                val reportPhotos = AppContainer.inspectionPhotoService.list(report.id)
                    .ifEmpty { hydrateEmbeddedSirPhotos(report) }
                reportPhotos.mapNotNull { photo ->
                    val thumbnail = AppContainer.inspectionPhotoService.resolveFile(photo, thumbnail = true)
                        ?.let { path -> runCatching { Files.readAllBytes(path) }.getOrNull() }
                        ?: return@mapNotNull null
                    ProjectPdfExportService.PhotoEvidence(
                        caption = listOfNotNull(
                            report.reportCode?.takeIf(String::isNotBlank),
                            report.inspectionDate.toString(),
                            photo.originalName.takeIf(String::isNotBlank)
                        ).joinToString(" · "),
                        thumbnail = thumbnail
                    )
                }
            }
            projectPdfExportService.export(
                project = project,
                monitoring = AppContainer.projectRepository.monitoringDetails(project.id),
                language = language,
                financialRecords = AppContainer.financialRecordService.filtered(project.id, null, null, null),
                inspectionReports = inspectionReports,
                inspectionPhotos = inspectionPhotos,
                procurementRecords = procurementRecords,
                documents = AppContainer.projectDocumentService.list(project.id)
            )
        }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"OMS_$safeCode-subproject-card.pdf\"")
        call.respondBytes(bytes, ContentType.Application.Pdf)
    }

    post("/api/v1/projects/{uuid}/restore") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
        val uuid = call.parameters["uuid"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        val project = projectService.getProjectByUuid(uuid) ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        if (!call.requireProjectAccess(session, uuid)) return@post
        if (!projectService.restoreProject(uuid)) return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        AppContainer.auditLogService.record(session.userId, "project_restored", project.projectType.name.lowercase(), project.id)
        call.respond(HttpStatusCode.NoContent)
    }

    get("/api/v1/projects/{uuid}/permanent-delete-dependencies") {
        call.requireRole("ADMIN") ?: return@get
        val uuid = call.parameters["uuid"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        call.respond(projectService.projectDependencies(uuid))
    }

    delete("/api/v1/projects/{uuid}/permanent") {
        val session = call.requireRole("ADMIN") ?: return@delete
        val uuid = call.parameters["uuid"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        val project = projectService.getProjectByUuid(uuid) ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        if (!project.isArchived) return@delete call.respond(HttpStatusCode.Conflict, ErrorResponse("NOT_ARCHIVED", "Archive the project before permanently deleting it."))
        val dependencies = projectService.projectDependencies(uuid)
        if (dependencies.isNotEmpty()) return@delete call.respond(HttpStatusCode.Conflict, ErrorResponse("HAS_DEPENDENCIES", dependencies.entries.joinToString("; ") { "${it.value} ${it.key}" }))
        if (!projectService.permanentlyDeleteProject(uuid)) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        AppContainer.auditLogService.record(session.userId, "permanent_delete", project.projectType.name.lowercase(), project.id, oldValues = "{\"name\":\"${project.name}\"}")
        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * Повертає всі інспекції проєкту.
     */
    get(
        "/api/v1/projects/{uuid}/inspection-reports"
    ) {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get

        val uuid =
            call.parameters["uuid"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,

                    ErrorResponse(
                        error = "VALIDATION_ERROR",
                        message =
                            "UUID проєкту відсутній."
                    )
                )

        val project =
            projectService.getProjectByUuid(
                uuid
            )

        if (project == null) {

            call.respond(
                HttpStatusCode.NotFound,

                ErrorResponse(
                    error = "NOT_FOUND",
                    message =
                        "Проєкт не знайдено."
                )
            )

            return@get
        }
        if (!call.requireProjectAccess(session, uuid)) return@get

        val reports =
            AppContainer
                .inspectionReportService
                .getProjectReports(
                    project.id
                )

        call.respond(

            reports.map {

                it.toResponse()
            }
        )
    }

    get("/api/v1/projects/{uuid}/health-safety-observations") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val uuid = call.parameters["uuid"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        val project = projectService.getProjectByUuid(uuid)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        if (!call.requireProjectAccess(session, uuid)) return@get
        val reports = AppContainer.inspectionReportService.getProjectReports(project.id)
        val observations = reports.flatMap { report ->
            AppContainer.inspectionReportFileService.healthSafetyObservations(report.id).map { observation ->
                HealthSafetyObservationResponse(
                    reportUuid = report.uuid.toString(),
                    inspectionDate = report.inspectionDate.toString(),
                    observation = observation.observation,
                    answer = observation.answer,
                    comment = observation.comment
                )
            }
        }
        call.respond(HealthSafetyObservationsResponse(reports.size, observations))
    }

    /**
     * Створює новий звіт інспекції.
     */
    post(
        "/api/v1/projects/{uuid}/inspection-reports"
    ) {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post

        val uuid =
            call.parameters["uuid"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,

                    ErrorResponse(
                        error = "VALIDATION_ERROR",
                        message =
                            "UUID проєкту відсутній."
                    )
                )

        val project =
            projectService.getProjectByUuid(
                uuid
            )

        if (project == null) {

            call.respond(
                HttpStatusCode.NotFound,

                ErrorResponse(
                    error = "NOT_FOUND",
                    message =
                        "Проєкт не знайдено."
                )
            )

            return@post
        }
        if (!call.requireProjectAccess(session, uuid)) return@post

        val request =
            call.receive<
                    CreateInspectionReportRequest
                    >()

        try {

            val report =
                AppContainer
                    .inspectionReportService
                    .createReport(

                        projectId =
                            project.id,

                        inspectionDate =
                            request.inspectionDate,

                        summary =
                            request.summary,

                        createdBy = session.userId,
                        reportCode = request.reportCode,
                        inspectionType = request.inspectionType,
                        latitude = request.latitude,
                        longitude = request.longitude
                    )

            call.respond(
                HttpStatusCode.Created,

                report.toResponse()
            )

        } catch (
            exception: IllegalArgumentException
        ) {

            call.respond(

                HttpStatusCode.BadRequest,

                ErrorResponse(
                    error = "VALIDATION_ERROR",

                    message =
                        exception.message
                            ?: "Помилка створення інспекції."
                )
            )
        }
    }
}

private data class SignedContractTotals(
    val contractAmountUah: Double?,
    val eibFinancingUah: Double?,
    val localFinancingUah: Double?,
    val localCofinancingPct: Double?
)

/** The approved register uses bilingual statuses; retain a single semantic test
 * here so the export cannot include tenders that have not become contracts. */
private fun ProcurementRecord.isSignedContract(): Boolean =
    purchaseStatus?.let { status ->
        status.contains("Договір укладено", ignoreCase = true) ||
            status.contains("Contract signed", ignoreCase = true)
    } == true

private fun ProcurementRecord.isWorksContract(): Boolean =
    sourceContractType.orEmpty().let { type ->
        type.contains("робот", ignoreCase = true) || type.contains("works", ignoreCase = true)
    }

private fun ProcurementRecord.isTechnicalSupervisionContract(): Boolean =
    sourceContractType.orEmpty().let { type ->
        type.contains("техніч", ignoreCase = true) || type.contains(" / ts", ignoreCase = true)
    }

private fun ProcurementRecord.isEngineerConsultantContract(): Boolean =
    sourceContractType.orEmpty().let { type ->
        type.contains("інженер", ignoreCase = true) ||
            type.contains("consult", ignoreCase = true) ||
            type.contains(" / csc", ignoreCase = true)
    }

/**
 * Calculates financing distribution from signed contracts only.  Local funding
 * is the contract remainder when the source provides the EIB amount; this is
 * more reliable than summing percentages across several contracts.
 */
private fun Iterable<ProcurementRecord>.signedContractTotals(): SignedContractTotals {
    val records = toList()
    val contractAmount = records.mapNotNull { it.contractAmountUah }.sum().takeIf { records.any { it.contractAmountUah != null } }
    val eibAmount = records.mapNotNull { it.realEibFinancingUah ?: it.estimatedEibUah }.sum()
        .takeIf { records.any { it.realEibFinancingUah != null || it.estimatedEibUah != null } }
    val localAmount = if (contractAmount != null && eibAmount != null) (contractAmount - eibAmount).coerceAtLeast(0.0) else null
    val localPercent = when {
        contractAmount != null && contractAmount > 0.0 && localAmount != null -> localAmount / contractAmount * 100.0
        else -> records.mapNotNull { it.realLocalCoFinancingPct ?: it.localFinancingPct }.average().takeUnless { it.isNaN() }
    }
    return SignedContractTotals(contractAmount, eibAmount, localAmount, localPercent)
}

private fun org.apache.poi.ss.usermodel.Row.setNumeric(column: Int, value: Double?) {
    value?.let { createCell(column).setCellValue(it) }
}
