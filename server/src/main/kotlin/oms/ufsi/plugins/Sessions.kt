package oms.ufsi.plugins

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import oms.ufsi.security.UserSession

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("oms_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.sameSite = "lax"
        }
    }
}
