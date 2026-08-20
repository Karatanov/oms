package oms.ufsi.security

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
        val modified = token.dropLast(1) + if (token.last() == 'a') "b" else "a"

        assertNull(JwtTokenService.verify(modified))
    }
}
