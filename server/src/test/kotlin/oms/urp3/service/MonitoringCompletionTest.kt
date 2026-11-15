package oms.urp3.service

import kotlin.test.*

class MonitoringCompletionTest {
    @Test fun `physical completion accepts percentages and fractions with exact boundaries`() {
        for ((text, expected) in listOf("98%" to 0.98, "0.98" to 0.98, "12,2%" to 0.122,
            "12.2" to 0.122, "0.5%" to 0.005, "0%" to 0.0, "100%" to 1.0, "1" to 1.0)) {
            assertEquals(expected, monitoringCompletionFraction(text), text)
            validateMonitoringCompletion(text, "TEST", "CC16")
        }
    }
    @Test fun `currency-like amount cannot be interpreted as physical completion`() {
        for (text in listOf("697 192,92", "101%", "100.0001%", "-1%", "NaN", "Infinity", "#VALUE!")) {
            assertNull(monitoringCompletionFraction(text), text)
            val error = assertFailsWith<IllegalArgumentException> { validateMonitoringCompletion(text, "TEST", "CC16") }
            assertContains(error.message.orEmpty(), "TEST")
            assertContains(error.message.orEmpty(), "CC16")
            assertContains(error.message.orEmpty(), "existing registry was not replaced")
        }
    }
    @Test fun `blank remains missing not zero and does not block a snapshot`() {
        assertNull(monitoringCompletionFraction(null))
        assertNull(monitoringCompletionFraction(" "))
        validateMonitoringCompletion("", "TEST", "CC16")
    }
}
