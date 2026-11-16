package oms.urp3.service

import kotlin.test.*

class MonitoringSourceNumberValidationTest {
    private fun number(value: String) = monitoringImportAmount(value, "TEST (fundingTotal)")

    @Test fun `malformed mixed funding cannot become a zero budget during import`() {
        val failure = assertFailsWith<IllegalArgumentException> { number("12,34.56") }
        assertTrue(failure.message.orEmpty().contains("TEST (fundingTotal)"))
        assertEquals(1234.56, number("1.234,56"))
        assertEquals(0.0, number("0"))
    }
}
