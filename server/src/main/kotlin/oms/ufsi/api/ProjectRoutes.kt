package oms.ufsi.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
        if (page < 1 || pageSize !in 1..100) {
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "page must be positive and pageSize must be between 1 and 100."))
        }
        val projects = projectService.searchProjects(
            status = call.request.queryParameters["status"],
            region = call.request.queryParameters["region"],
            search = call.request.queryParameters["search"]
        )
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
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post

        val request =
            call.receive<CreateProjectRequest>()

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

                    managerId = request.managerId,
                )

            call.respond(
                HttpStatusCode.Created,
                project.toResponse()
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

        call.respond(

            ProjectDetailsResponse(

                data =
                    project.toResponse(),

                financialSummary = AppContainer.financialRecordService.summary(project).let {
                    FinancialSummaryResponse(it.budgetPlanned, it.amountSpent, it.budgetRemaining, it.completionPct)
                }
            )
        )
    }

    patch("/api/v1/projects/{uuid}") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@patch
        val uuid = call.parameters["uuid"] ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        try {
            val project = projectService.updateProject(uuid, call.receive<UpdateProjectRequest>())
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
            call.respond(project.toResponse())
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid project data."))
        }
    }

    delete("/api/v1/projects/{uuid}") {
        call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@delete
        val uuid = call.parameters["uuid"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Project UUID is required."))
        if (!projectService.deleteProject(uuid)) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * Повертає всі інспекції проєкту.
     */
    get(
        "/api/v1/projects/{uuid}/inspection-reports"
    ) {

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
