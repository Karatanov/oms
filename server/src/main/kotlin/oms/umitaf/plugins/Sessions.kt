package oms.umitaf.plugins

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import oms.umitaf.security.UserSession

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("oms_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.sameSite = "none"
            cookie.secure = true
        }
    }
}
