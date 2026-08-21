package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.*

/**
 * Маршрути роботи з проєктами.
 */
fun Route.projectRoutes() {

    val projectService =
        AppContainer.projectService

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
        val response =
                    ProjectListResponse(

                data =
                    pagedProjects.map { project ->

                        project.toResponse(projectUuidById[project.parentProjectId])
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

            val result = projectService.updateProject(
                project.uuid.toString(),
                UpdateProjectRequest(
                    description = request.description,
                    endDate = request.endDate,
                    designContractSigningDate = request.designContractSigningDate,
                    constructionContractSigningDate = request.constructionContractSigningDate,
                    constructionStartDate = request.constructionStartDate,
                    projectedCompletionTime = request.projectedCompletionTime,
                    currency = request.currency,
                    contractorName = request.contractorName
                )
            ) ?: project

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
                        createCell(5).setCellValue(project.region)
                        createCell(6).setCellValue(project.city)
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

                        createdBy = session.userId
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
