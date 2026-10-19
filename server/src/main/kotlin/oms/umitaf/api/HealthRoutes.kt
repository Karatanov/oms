package oms.umitaf.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.umitaf.dto.HealthResponse
import oms.umitaf.dto.VersionResponse
import oms.umitaf.plugins.DatabaseReadiness

/**
 * Реєструє службові маршрути застосунку.
 *
 * Такі маршрути не пов'язані з бізнес-логікою системи,
 * а використовуються для перевірки працездатності сервера.
 */
fun Routing.healthRoutes() {

    // This is deliberately independent from /health: a load balancer only
    // needs liveness, whereas the deployment workflow must verify the exact
    // immutable image revision that reached the public server.
    val revision = System.getenv("OMS_GIT_REVISION")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(12)
        ?: "local"

    /** Lightweight liveness check used by the container and load balancer. */
    get("/health") {
        call.respond(HealthResponse(status = "UP"))
    }

    get("/version") {
        call.respond(VersionResponse(revision = revision))
    }

    /**
     * Database-aware readiness check for deployment diagnostics.
     *
     * Unlike [/health], this reports whether OMS can currently serve its
     * database-backed API routes. It deliberately returns no database details.
     */
    get("/ready") {
        val ready = DatabaseReadiness.isReady()
        call.respond(
            if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthResponse(status = if (ready) "READY" else "NOT_READY")
        )
    }

    // Keep the existing API health-check URL for local tools and API clients.
    get("/api/v1/health") {
        call.respond(HealthResponse(status = "UP"))
    }

    get("/api/v1/ready") {
        val ready = DatabaseReadiness.isReady()
        call.respond(
            if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthResponse(status = if (ready) "READY" else "NOT_READY")
        )
    }
}
