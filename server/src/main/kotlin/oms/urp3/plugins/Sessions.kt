package oms.urp3.plugins

import oms.urp3.config.Deployment
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import oms.urp3.security.UserSession

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>(if (Deployment.legacy) "oms_session" else "__Host-oms_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.sameSite = if (Deployment.legacy) "none" else "lax"
            if (!Deployment.legacy) {
                val secret = requireNotNull(System.getenv("OMS_JWT_SECRET"))
                require(secret.length >= 32)
                transform(SessionTransportTransformerMessageAuthentication(secret.toByteArray()))
            }
            cookie.secure = true
        }
    }
}
