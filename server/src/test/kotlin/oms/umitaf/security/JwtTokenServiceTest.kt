package oms.umitaf.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtTokenServiceTest {
    @Test
    fun `issued token resolves its subject and role`() {
        val token = JwtTokenService.issue(42, "project_manager")

        val session = JwtTokenService.verify(token)

        assertEquals(42, session?.userId)
        assertEquals("PROJECT_MANAGER", session?.roleCode)
    }

    @Test
    fun `modified token is rejected`() {
        val token = JwtTokenService.issue(42, "ADMIN")
        val parts = token.split('.')
        val modifiedPayload = parts[1].dropLast(1) + if (parts[1].last() == 'a') "b" else "a"
        val modified = "${parts[0]}.$modifiedPayload.${parts[2]}"

        assertNull(JwtTokenService.verify(modified))
    }
}
