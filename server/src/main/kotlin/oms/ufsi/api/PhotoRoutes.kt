package oms.ufsi.api
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import oms.ufsi.config.AppContainer
import oms.ufsi.dto.*
import java.nio.file.Files
import java.nio.file.Path
fun Route.photoRoutes(){route("/api/v1/inspection-reports/{reportUuid}/photos"){
 get{val r=call.photoReport()?:return@get;call.respond(AppContainer.inspectionPhotoService.list(r.id).map{it.toResponse(r.uuid.toString())})}
 post{call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@post;val r=call.photoReport()?:return@post;val m=call.receiveMultipart();var result:oms.ufsi.domain.InspectionPhoto?=null;try{m.forEachPart{p->if(p is PartData.FileItem&&p.name=="file"){val n=p.originalFileName?:throw IllegalArgumentException("File name is required.");result=AppContainer.inspectionPhotoService.upload(r.id,n,p.contentType?.toString(),p.provider().readRemaining(10_000_001).readByteArray())};p.dispose()};call.respond(HttpStatusCode.Created,(result?:throw IllegalArgumentException("Field file is required.")).toResponse(r.uuid.toString()))}catch(e:IllegalArgumentException){call.respond(HttpStatusCode.BadRequest,ErrorResponse("VALIDATION_ERROR",e.message?:"Invalid request."))}}
 put("{photoUuid}/main"){call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@put;val r=call.photoReport()?:return@put;val p=call.parameters["photoUuid"]?.let{AppContainer.inspectionPhotoService.setMain(r.id,it)}?:return@put call.respond(HttpStatusCode.NotFound,ErrorResponse("NOT_FOUND","Photo not found."));call.respond(p.toResponse(r.uuid.toString()))}
 delete("{photoUuid}"){call.requireRole("ADMIN", "PROJECT_MANAGER", "INSPECTOR") ?: return@delete;val r=call.photoReport()?:return@delete;val ok=call.parameters["photoUuid"]?.let{AppContainer.inspectionPhotoService.delete(r.id,it)}?:false;if(!ok)return@delete call.respond(HttpStatusCode.NotFound,ErrorResponse("NOT_FOUND","Photo not found."));call.respond(HttpStatusCode.NoContent)}
 get("{photoUuid}/{kind}"){val r=call.photoReport()?:return@get;val p=call.parameters["photoUuid"]?.let{AppContainer.inspectionPhotoService.get(r.id,it)}?:return@get call.respond(HttpStatusCode.NotFound,ErrorResponse("NOT_FOUND","Photo not found."));val path=Path.of(if(call.parameters["kind"]=="thumbnail")p.thumbnailPath else p.storagePath);if(!Files.isRegularFile(path))return@get call.respond(HttpStatusCode.NotFound);call.respondFile(path.toFile())}
}}
private suspend fun io.ktor.server.application.ApplicationCall.photoReport()=parameters["reportUuid"]?.let{AppContainer.inspectionReportService.getByUuid(it)}?:run{respond(HttpStatusCode.NotFound,ErrorResponse("NOT_FOUND","Inspection report not found."));null}
