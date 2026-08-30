package oms.ufsi.api

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import oms.ufsi.config.AppContainer
import oms.ufsi.domain.ProjectDocument
import oms.ufsi.dto.ErrorResponse
import oms.ufsi.dto.toResponse
import java.io.ByteArrayInputStream
import java.net.URLEncoder

fun Route.documentRoutes() {
    get("/api/v1/documents") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val accessibleProjects = AppContainer.projectService.getAllProjects().filter { project ->
            !session.roleCode.equals("PROJECT_MANAGER", ignoreCase = true) ||
                AppContainer.projectService.isManagedBy(project.uuid.toString(), session.userId)
        }
        val projectUuidsById = accessibleProjects.associate { it.id to it.uuid.toString() }
        call.respond(AppContainer.projectDocumentService.listAll().mapNotNull { document ->
            projectUuidsById[document.projectId]?.let { projectUuid ->
                oms.ufsi.dto.ProjectDocumentListItemResponse(projectUuid, document.toResponse())
            }
        })
    }

    route("/api/v1/projects/{projectUuid}/documents") {
    get {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val project = call.documentProject() ?: return@get
        if (!call.requireProjectAccess(session, project.uuid.toString())) return@get
        val type = call.request.queryParameters["doc_type"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val related = call.request.queryParameters["related_entity"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val query = call.request.queryParameters["search"]?.trim()?.takeIf { it.isNotEmpty() }
        val documents = AppContainer.projectDocumentService.list(project.id).filter { document ->
            (type == null || document.docType == type) &&
                (related == null || document.relatedEntity == related) &&
                (query == null || document.originalName.contains(query, ignoreCase = true) || document.description.orEmpty().contains(query, ignoreCase = true))
        }
        call.respond(documents.map(ProjectDocument::toResponse))
    }

    post {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@post
        val project = call.documentProject() ?: return@post
        if (!call.requireProjectAccess(session, project.uuid.toString())) return@post
        var documentType: String? = null
        var relatedEntity: String? = null
        var relatedId: Long? = null
        var description: String? = null
        var fileName: String? = null
        var contentType: String? = null
        var bytes: ByteArray? = null
        try {
            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> when (part.name) {
                        "docType" -> documentType = part.value
                        "relatedEntity" -> relatedEntity = part.value
                        "relatedId" -> relatedId = part.value.toLongOrNull() ?: throw IllegalArgumentException("relatedId must be a number.")
                        "description" -> description = part.value
                    }
                    is PartData.FileItem -> if (part.name == "file") {
                        fileName = part.originalFileName ?: throw IllegalArgumentException("File name is required.")
                        contentType = part.contentType?.toString()
                        bytes = part.provider().readRemaining(50_000_001).readByteArray()
                    }
                    else -> Unit
                }
                part.dispose()
            }
            val uploaded = AppContainer.projectDocumentService.upload(
                project.id,
                documentType ?: throw IllegalArgumentException("Field docType is required."),
                fileName ?: throw IllegalArgumentException("Field file is required."),
                contentType,
                ByteArrayInputStream(bytes ?: throw IllegalArgumentException("Field file is required.")),
                relatedEntity,
                relatedId,
                description
            )
            call.respond(HttpStatusCode.Created, uploaded.toResponse())
        } catch (exception: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", exception.message ?: "Invalid request."))
        }
    }

    get("{documentUuid}/download") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER") ?: return@get
        val project = call.documentProject() ?: return@get
        if (!call.requireProjectAccess(session, project.uuid.toString())) return@get
        val document = call.parameters["documentUuid"]?.let { AppContainer.projectDocumentService.get(project.id, it) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Document not found."))
        val path = AppContainer.projectDocumentService.resolveFile(document)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Document file is unavailable."))
        val encodedName = URLEncoder.encode(document.originalName, Charsets.UTF_8).replace("+", "%20")
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename*=UTF-8''$encodedName")
        call.respondFile(path.toFile())
    }

    delete("{documentUuid}") {
        val session = call.requireRole("ADMIN", "PROJECT_MANAGER") ?: return@delete
        val project = call.documentProject() ?: return@delete
        if (!call.requireProjectAccess(session, project.uuid.toString())) return@delete
        val uuid = call.parameters["documentUuid"] ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Document not found."))
        if (!AppContainer.projectDocumentService.delete(project.id, uuid)) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Document not found."))
        call.respond(HttpStatusCode.NoContent)
    }
    }
}

private suspend fun ApplicationCall.documentProject() = parameters["projectUuid"]
    ?.let { AppContainer.projectService.getProjectByUuid(it) }
    ?: run {
        respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Project not found."))
        null
    }
