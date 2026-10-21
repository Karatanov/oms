package oms.urp3.plugins

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import oms.urp3.security.UserSession

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
