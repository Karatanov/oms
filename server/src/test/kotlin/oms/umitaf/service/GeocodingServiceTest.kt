package oms.umitaf.service

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.*

class GeocodingServiceTest {
    @Test fun `normalizes duplicate city region country and whitespace`() {
        assertEquals("Sumska 74, Kharkiv, Україна", composeGeocodingAddress(" Sumska\u00a0 74, Kharkiv, Ukraine ", "Kharkiv", ""))
        assertEquals("Сумська 74, Харків, Харківська область, Україна",
            composeGeocodingAddress("Сумська 74, м. Харків, Харківська область", "Харків", "Харківська"))
    }

    private fun withProvider(body: String, status: Int = 200, test: (GeocodingService) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/search") { exchange ->
            val params = exchange.requestURI.rawQuery.split('&').associate {
                val pair = it.split('=', limit = 2)
                pair[0] to URLDecoder.decode(pair[1], Charsets.UTF_8)
            }
            assertEquals("Sumska 74, Kharkiv, Україна", params["q"])
            assertEquals("ua", params["countrycodes"])
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { test(GeocodingService("http://127.0.0.1:${server.address.port}/search")) }
        finally { server.stop(0) }
    }

    @Test fun `encoded address parses provider result`() = withProvider("""[{"lat":"50.0058253","lon":"36.2367038","display_name":"Сумська"}]""") {
        val result = assertNotNull(it.geocode("Sumska 74", "Kharkiv", ""))
        assertEquals(50.0058253, result.latitude)
        assertEquals(36.2367038, result.longitude)
    }
    @Test fun `empty search is distinct from provider failure`() {
        withProvider("[]") { assertNull(it.geocode("Sumska 74", "Kharkiv", "")) }
        withProvider("Unavailable", 503) { assertFailsWith<IllegalStateException> { it.geocode("Sumska 74", "Kharkiv", "") } }
    }
    @Test fun `invalid coordinates are not accepted`() = withProvider("""[{"lat":"NaN","lon":"999"}]""") {
        assertNull(it.geocode("Sumska 74", "Kharkiv", ""))
    }
}
