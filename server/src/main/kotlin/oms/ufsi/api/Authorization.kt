package oms.ufsi.api
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import oms.ufsi.dto.ErrorResponse
import oms.ufsi.security.UserSession
internal suspend fun ApplicationCall.requireRole(vararg roles:String):UserSession?{val s=sessions.get<UserSession>()?:run{respond(HttpStatusCode.Unauthorized,ErrorResponse("AUTHENTICATION_REQUIRED","Login is required."));return null};if(s.roleCode.uppercase() !in roles.map{it.uppercase()}){respond(HttpStatusCode.Forbidden,ErrorResponse("FORBIDDEN","Your role cannot perform this action."));return null};return s}
