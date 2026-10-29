package oms.urp3.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import oms.urp3.dto.ErrorResponse
import oms.urp3.service.FeedbackRequest
import oms.urp3.service.FeedbackService

fun Route.feedbackRoutes(service: FeedbackService = FeedbackService()) {
    val roles = arrayOf("ADMIN", "PROJECT_MANAGER", "INSPECTOR", "VIEWER", "CONTRACTOR", "GUEST")
    get("/api/v1/feedback") {
        call.requireRole(*roles) ?: return@get
        call.respond(service.availability())
    }
    post("/api/v1/feedback") {
        val session = call.requireRole(*roles) ?: return@post
        val request = call.receive<FeedbackRequest>()
        try {
            call.respond(HttpStatusCode.Created, service.submit(request, session.userId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_FEEDBACK", "Check the page, type and description (1–5000 characters)."))
        } catch (_: Exception) {
            // Do not expose credential paths, Google responses or access tokens to clients.
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("FEEDBACK_UNAVAILABLE", "Feedback could not be sent. Please retry later."))
        }
    }
}
