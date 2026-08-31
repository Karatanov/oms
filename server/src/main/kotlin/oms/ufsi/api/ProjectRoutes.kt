package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Маршрути роботи з проєктами.
 */
fun Route.projectRoutes() {

    val projectService =
        AppContainer.projectService

    get("/api/v1/exchange-rates/eur") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@get
        try {
            val (date, rate) = withContext(Dispatchers.IO) {
                AppContainer.nbuExchangeRateService.eurRate(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Kyiv")))
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
            val result = AppContainer.geocodingService.geocode(request.address, request.city, request.region)
            if (result == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Address was not found."))
            else call.respond(result)
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid address."))
        } catch (exception: Exception) {
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("GEOCODING_ERROR", "Unable to resolve the address."))
        }
    }

    /**
     * Повертає перелік проєктів.
     *
     * На поточному етапі
     * підтримується лише
     * перша сторінка.
     */
    get("/api/v1/projects") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "GUEST") ?: return@get
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
        if (page < 1 || pageSize !in 1..100) {
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "page must be positive and pageSize must be between 1 and 100."))
        }
        val projects = projectService.searchProjects(
            status = call.request.queryParameters["status"],
            region = call.request.queryParameters["region"],
            search = call.request.queryParameters["search"]
        ).filter { project ->
            !session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true) ||
                projectService.isManagedBy(project.uuid.toString(), session.userId)
        }
        val total = projects.size.toLong()
        val pagedProjects = projects.drop((page - 1) * pageSize).take(pageSize)

        val projectUuidById = projects.associate { project -> project.id to project.uuid.toString() }
        val monitoringByProjectId = projectService.monitoringDetailsByProjectIds(pagedProjects.map { it.id })
        val response =
                    ProjectListResponse(

                data =
                    pagedProjects.map { project ->

                        project.toResponse(projectUuidById[project.parentProjectId], monitoringByProjectId[project.id])
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

        call.respond(response)
    }

    /**
     * Створює новий проєкт.
     */
    post("/api/v1/projects") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post

        val request = call.receive<CreateProjectRequest>()
        if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true) &&
            request.parentProjectUuid != null && !call.requireProjectAccess(session, request.parentProjectUuid)
        ) return@post

        try {

            val result = transaction {
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
                    designContractSigningDate = request.designContractSigningDate,
                    designStartDate = request.designStartDate,
                    designPlannedEndDate = request.designPlannedEndDate,
                    constructionContractSigningDate = request.constructionContractSigningDate,
                    constructionStartDate = request.constructionStartDate,
                    projectedCompletionTime = request.projectedCompletionTime,
                    currency = request.currency,
                    contractorName = request.contractorName,
                    designerName = request.designerName,
                    designContractNumber = request.designContractNumber,
                    designContractTerm = request.designContractTerm,
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

        call.respond(

            ProjectDetailsResponse(

                data =
                    project.toResponse(),

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
                programmeDetails = AppContainer.projectRepository.programmeDetails(project.id)?.let {
                    ProgrammeDetailsResponse(
                        it.implementor, it.financingInstitution, it.financeContractNumber, it.serapisNumber,
                        it.agreementDate.toString(), it.loanAmount.toPlainString(), it.loanCurrency,
                        it.sourceWorkbook, it.sourceSnapshotDate?.toString()
                    )
                },
                monitoringDetails = AppContainer.projectRepository.monitoringDetails(project.id)?.let {
                    ProjectMonitoringDetailsResponse(
                        it.sourceBatchId, it.sourceSubprojectId, it.sourceLotId, it.nameEn, it.oblastCode,
                        it.municipalityNameUk, it.municipalityNameEn, it.settlementNameEn, it.priorityAreaSource,
                        it.projectManagerNameUk, it.projectManagerNameEn, it.projectManagerOrgId,
                        it.beneficiaryNameUk, it.beneficiaryNameEn, it.beneficiaryOrgId, it.dreamProjectId,
                        it.dreamProjectUrl, it.applicationId, it.dreamApplicationId,
                        it.constructionProcurementStatus, it.constructionWorkStatus, it.geocodeAccuracy,
                        it.geocodeQuery, it.geocodeDisplayName, it.sourceRows, it.sourceWorkbook
                    )
                }
            )
        )
    }

    patch("/api/v1/projects/bulk-status") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@patch
        try {
            val request = call.receive<BulkProjectUpdateRequest>()
            if (session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true) &&
                request.projectUuids.any { !projectService.isManagedBy(it, session.userId) }
            ) return@patch call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "You can update only projects assigned to you."))
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
            val manager = AppContainer.userService.getAllUsers().firstOrNull { it.id == request.managerId }
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
            (requestedUuids.isEmpty() || project.uuid.toString() in requestedUuids) &&
                (session.roleCode.equals("ADMIN", ignoreCase = true) || projectService.isManagedBy(project.uuid.toString(), session.userId))
        }
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
                workbook.write(this)
            }
        }
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

    delete("/api/v1/projects/{uuid}") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@delete
        val uuid = call.parameters["uuid"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        val project = projectService.getProjectByUuid(uuid) ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        if (!call.requireProjectAccess(session, uuid)) return@delete
        if (!projectService.deleteProject(uuid)) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        AppContainer.auditLogService.record(session.userId, "project_deleted", project.projectType.name.lowercase(), project.id)
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
