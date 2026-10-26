package oms.urp3.service

import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.*

class ProcurementWorkbookDownloadTest {
    private class Response(val status: Int = 200, val type: String = "application/octet-stream", val bytes: ByteArray = byteArrayOf(0x50, 0x4b)) :
        HttpURLConnection(URI("https://example.test").toURL()) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = type
        override fun getInputStream() = bytes.inputStream()
    }

    @Test fun `private source receives bearer token and cannot redirect it`() {
        val response = Response()
        ProcurementWorkbookSource.download(null, "key.json", { "test-token" }) { uri ->
            assertEquals("www.googleapis.com", uri.host)
            assertTrue(uri.query.contains("alt=media"))
            response
        }
        assertEquals("Bearer test-token", response.getRequestProperty("Authorization"))
        assertFalse(response.instanceFollowRedirects)
        assertTrue(response.disconnected)
    }

    @Test fun `custom source never loads or sends Google credentials`() {
        val response = Response()
        ProcurementWorkbookSource.download("https://example.test/book.xlsx", "key.json", { error("Must not load key") }) {
            assertEquals("example.test", it.host)
            response
        }
        assertNull(response.getRequestProperty("Authorization"))
        assertTrue(response.disconnected)
    }

    @Test fun `HTML errors and non workbook responses are rejected and disconnected`() {
        for (response in listOf(Response(403), Response(302), Response(type = "text/html"), Response(bytes = "invalid".toByteArray()))) {
            assertFailsWith<IllegalArgumentException> {
                ProcurementWorkbookSource.download(null, "key.json", { "test-token" }) { response }
            }
            assertTrue(response.disconnected)
        }
    }

    @Test fun `built in source falls back to the approved Sheets export when Drive access returns 404`() {
        val hosts = mutableListOf<String>()
        val bytes = ProcurementWorkbookSource.download(null, "key.json", { "test-token" }) { uri ->
            hosts += uri.host
            if (uri.host == "www.googleapis.com") Response(status = 404) else Response()
        }
        assertContentEquals(byteArrayOf(0x50, 0x4b), bytes)
        assertEquals(listOf("www.googleapis.com", "docs.google.com"), hosts)
    }
}
