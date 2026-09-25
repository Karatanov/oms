package oms.umitaf

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import oms.umitaf.module
import kotlin.test.*

class ApplicationTest {

    @Test fun guestBearerRestoresSessionWithoutCookiesAndCorsAllowsAuthorization() = testApplication {
        application { module(configureDatabase = false) }
        val guest = client.post("/api/v1/auth/guest")
        assertEquals(HttpStatusCode.OK, guest.status)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(guest.bodyAsText()) as kotlinx.serialization.json.JsonObject
        val token = (json.getValue("accessToken") as kotlinx.serialization.json.JsonPrimitive).content
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/auth/session").status)
        val restored = client.get("/api/v1/auth/session") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, restored.status)
        assertTrue(restored.bodyAsText().contains("\"guest\":true"))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/auth/session") { bearerAuth("invalid") }.status)
        val preflight = client.options("/api/v1/auth/session") {
            header(HttpHeaders.Origin, "https://karatanov.github.io")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization")
        }
        assertEquals(HttpStatusCode.OK, preflight.status)
        assertTrue(preflight.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().contains("authorization", true))
    }

    @Test
    fun testRoot() = testApplication {
        application {
            module(configureDatabase = false)
        }
        val response = createClient { followRedirects = false }.get("/")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("https://karatanov.github.io/oms/", response.headers[HttpHeaders.Location])
    }
}
