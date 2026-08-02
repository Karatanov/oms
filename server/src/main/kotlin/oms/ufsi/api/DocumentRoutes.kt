package oms.ufsi.api

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.ErrorResponse
import oms.ufsi.dto.toResponse
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

fun Route.documentRoutes() {
    route("/api/v1/projects/{projectUuid}/documents") {
        get { val project = call.documentProject() ?: return@get; call.respond(AppContainer.projectDocumentService.list(project.id).map { it.toResponse() }) }
        post {
            val project = call.documentProject() ?: return@post; val multipart = call.receiveMultipart(); var uploaded: oms.ufsi.domain.ProjectDocument? = null; var type: String? = null
            try { multipart.forEachPart { part -> when(part) { is PartData.FormItem -> if(part.name=="docType") type=part.value; is PartData.FileItem -> if(part.name=="file") { val name=part.originalFileName ?: throw IllegalArgumentException("File name is required."); val bytes=part.provider().readRemaining(50_000_001).readByteArray(); require(bytes.size<=50_000_000) { "File size must not exceed 50 MB." }; uploaded=AppContainer.projectDocumentService.upload(project.id, type ?: throw IllegalArgumentException("Field docType is required."), name, part.contentType?.toString(), ByteArrayInputStream(bytes)) }; else -> {} }; part.dispose() }; call.respond(HttpStatusCode.Created, (uploaded ?: throw IllegalArgumentException("Field file is required.")).toResponse()) } catch(e: IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR",e.message?:"Invalid request.")) }
        }
        get("{documentUuid}/download") { val project=call.documentProject() ?: return@get; val doc=call.parameters["documentUuid"]?.let { AppContainer.projectDocumentService.get(project.id,it) } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND","Document not found.")); val path=Path.of(doc.storagePath); if(!Files.isRegularFile(path)) return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND","Document file is unavailable.")); call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName,doc.originalName).toString()); call.respondFile(path.toFile()) }
    }
}
private suspend fun io.ktor.server.application.ApplicationCall.documentProject() = parameters["projectUuid"]?.let { AppContainer.projectService.getProjectByUuid(it) } ?: run { respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND","Project not found.")); null }
