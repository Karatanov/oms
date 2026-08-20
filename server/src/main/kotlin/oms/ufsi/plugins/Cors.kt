package oms.ufsi.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/** Enables the local Compose web client to use the cookie-based MVP API. */
fun Application.configureCors() {
    val configuredHosts = environment.config.propertyOrNull("cors.allowedHost")
        ?.getString()
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        .orEmpty()

    install(CORS) {
        allowHost("localhost:8081", schemes = listOf("http"))
        allowHost("127.0.0.1:8081", schemes = listOf("http"))
        allowHost("localhost:8082", schemes = listOf("http"))
        allowHost("127.0.0.1:8082", schemes = listOf("http"))
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)
        allowNonSimpleContentTypes = true
        allowCredentials = true

        configuredHosts.forEach { value ->
                val secure = value.startsWith("https://")
                val host = value.removePrefix("https://").removePrefix("http://").trimEnd('/')
                allowHost(host, schemes = listOf(if (secure) "https" else "http"))
        }
    }
}
