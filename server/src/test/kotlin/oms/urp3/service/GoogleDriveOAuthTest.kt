package oms.urp3.service

import java.net.URI
import java.net.URLDecoder
import kotlin.test.*

class GoogleDriveOAuthTest {
    private fun start(browser: String): Map<String, String> = URI(GoogleDriveOAuth.begin(42, browser, "test-client", "https://example.test/callback"))
        .rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }

    @Test fun `authorization requires PKCE and does not ask for offline access`() {
        val query = start(GoogleDriveOAuth.nonce())
        assertEquals("S256", query["code_challenge_method"])
        assertEquals(43, query.getValue("code_challenge").length)
        assertEquals("online", query["access_type"])
        assertEquals(GoogleDriveOAuth.scope, query["scope"])
    }

    @Test fun `wrong user or browser cannot consume state and denied consent cannot be replayed`() {
        val browser = GoogleDriveOAuth.nonce()
        val state = start(browser).getValue("state")
        assertFailsWith<IllegalArgumentException> { GoogleDriveOAuth.complete(43, browser, state, null, true) }
        assertFailsWith<IllegalArgumentException> { GoogleDriveOAuth.complete(42, "another-browser", state, null, true) }
        val cancelled = assertFailsWith<IllegalArgumentException> { GoogleDriveOAuth.complete(42, browser, state, null, true) }
        assertEquals("Google authorization was cancelled.", cancelled.message)
        val replay = assertFailsWith<IllegalArgumentException> { GoogleDriveOAuth.complete(42, browser, state, null, true) }
        assertTrue(replay.message.orEmpty().contains("expired"))
    }

    @Test fun `new attempt replaces old state in same browser`() {
        val browser = GoogleDriveOAuth.nonce()
        val first = start(browser).getValue("state")
        val second = start(browser).getValue("state")
        assertNotEquals(first, second)
        val invalid = assertFailsWith<IllegalArgumentException> { GoogleDriveOAuth.complete(42, browser, first, null, true) }
        assertTrue(invalid.message.orEmpty().contains("expired"))
        assertFailsWith<IllegalArgumentException> { GoogleDriveOAuth.consume(42, browser, first) }
    }
}
