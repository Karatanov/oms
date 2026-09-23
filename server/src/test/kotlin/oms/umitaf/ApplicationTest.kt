package oms.umitaf

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import oms.umitaf.module
import kotlin.test.*

class ApplicationTest {

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
