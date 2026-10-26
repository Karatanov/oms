package oms.urp3.plugins

import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import oms.urp3.api.*
import oms.urp3.security.UserSession
import java.io.File

/**
 * Ktor exposes the common Cache-Control directives but not HTTP's `immutable`
 * extension. It is safe only for content-addressed browser assets: each
 * production Compose bundle gets a fresh file name whenever it changes.
 */
private object ImmutableAssetCacheControl : CacheControl(null) {
    override fun toString(): String = "immutable"
}

/**
 * Реєструє всі HTTP-маршрути застосунку.
 *
 * A VPS deployment provides OMS_WEB_DIR so the browser application and API
 * share one origin. The redirect preserves the public-host fallback when that
 * directory is not configured.
 */
fun Application.configureRouting() {

    routing {

        // Службові маршрути системи.
        healthRoutes()

        // Маршрути роботи з ролями.
        roleRoutes()

        // Маршрути роботи з користувачами.
        userRoutes()

        // Маршрути автентифікації.
        authRoutes()
        googleDriveOAuthRoutes()

        // Маршрути роботи з проєктами.
        projectRoutes()

        mapRoutes()

        inspectionRoutes()

        financialRoutes()

        procurementRoutes()

        documentRoutes()
        photoRoutes()
        dashboardRoutes()

        // URLs published by early GitHub Pages and API-only deployments. They
        // were never public documents: the auth endpoints are POST-only now,
        // and the `/oms/` prefix belonged to the former Pages site. Redirect
        // crawler GET requests to the single public entry point so Search
        // Console no longer records these historical routes as 404 pages.
        listOf("/auth/login", "/auth/guest", "/oms/app.html").forEach { legacyPath ->
            get(legacyPath) {
                call.respondRedirect("https://ua-oms.com/", permanent = true)
            }
        }

        val frontendDirectory = System.getenv("OMS_WEB_DIR")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::File)
        if (frontendDirectory?.isDirectory == true) {
            // Keep the initial page independent from the large Kotlin/JS
            // application bundle. The bundle is requested only after login.
            get("/") {
                // This page is the release entry point. It can reference a new
                // fingerprinted Compose bundle on every deployment, so never
                // let a browser reuse it heuristically from its HTTP cache.
                call.response.headers.append(HttpHeaders.CacheControl, "public, no-cache")
                call.respondFile(File(frontendDirectory, "login.html"))
            }
            // Bookmarks and stale browser history can point straight at the
            // heavyweight Compose entry point. Keep an unauthenticated visit
            // lightweight as well: only an established server session may
            // receive app.html and download the WASM application.
            get("/app.html") {
                val page = if (call.sessions.get<UserSession>() == null) "login.html" else "app.html"
                // Let search crawlers read this directive instead of blocking
                // the URL in robots.txt. A disallow rule prevents Google from
                // seeing `noindex` and leads Search Console to report a
                // misleading "blocked by robots.txt" exclusion. The heavy
                // authenticated workspace must remain out of search results.
                call.response.headers.append("X-Robots-Tag", "noindex, nofollow, noarchive")
                // Keep the application shell in step with its fingerprinted
                // assets after a deployment. The assets themselves remain
                // immutable and are cached for a year below.
                call.response.headers.append(HttpHeaders.CacheControl, "public, no-cache")
                call.respondFile(File(frontendDirectory, page))
            }
            // `index.html` is the static Compose shell retained for existing
            // bookmarks. The public canonical entry point is `/`, so let
            // crawlers fetch this legacy URL only to receive an unambiguous
            // noindex response.
            get("/index.html") {
                call.response.headers.append("X-Robots-Tag", "noindex, nofollow, noarchive")
                call.response.headers.append(HttpHeaders.CacheControl, "public, no-cache")
                call.respondFile(File(frontendDirectory, "index.html"))
            }
            staticFiles("/", frontendDirectory, index = "index.html") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
                cacheControl { file ->
                    val fingerprinted = Regex("""composeApp\.[0-9a-f]{12}\.js|[0-9a-f]{20}\.wasm""")
                        .matches(file.name)
                    // The Skiko runtime is versioned with the fingerprint of
                    // its owning JS bundle (as a query string in the bundle),
                    // so it is safe to retain it just like a hashed asset.
                    if (fingerprinted || file.name == "skiko.wasm") {
                        listOf(
                            CacheControl.MaxAge(maxAgeSeconds = 31_536_000, visibility = CacheControl.Visibility.Public),
                            ImmutableAssetCacheControl
                        )
                    } else {
                        // HTML and non-fingerprinted assets must revalidate so a
                        // release never strands browsers on stale entry points.
                        listOf(CacheControl.NoCache(CacheControl.Visibility.Public))
                    }
                }
            }
        } else {
            // Production VPS Compose always sets OMS_WEB_DIR. Keep a safe
            // public-host fallback for incomplete server configuration.
            get("/") {
                call.respondRedirect("https://ua-oms.com/", permanent = false)
            }
        }
    }
}
