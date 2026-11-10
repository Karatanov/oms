package oms.urp3.service

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialProgressPaymentsTest {
    private fun payment(code: String, amount: Double, year: Int = 2026) =
        PaymentMonitoringWorkbookSource.Transaction(code, code, 8, LocalDate.of(year, 2, 1), amount, null, "test")

    @Test fun `empty approved register does not read the legacy ledger`() {
        assertEquals(emptyMap(), financialProgressPayments(emptyList(), mapOf("KH08_07" to 1L), null) {
            error("An empty imported register must not resurrect legacy payments")
        })
    }

    @Test fun `only an absent snapshot permits legacy fallback`() {
        var calls = 0
        assertEquals(mapOf(1L to 10.0), financialProgressPayments(null, emptyMap(), null) {
            calls++
            mapOf(1L to 10.0)
        })
        assertEquals(1, calls)
    }

    @Test fun `lot payments aggregate to their parent without services or other years`() {
        val rows = listOf(payment("KH08_07#Lot1_W", 10.25), payment("KH08_07#Lot2_W", 20.50),
            payment("KH08_07_TS", 300.0), payment("KH08_07_CSC", 400.0),
            payment("KH08_07_W", 500.0, 2025), payment("VN08_01_W", 600.0))
        assertEquals(mapOf(1L to 30.75), financialProgressPayments(rows, mapOf("KH08_07" to 1L), 2026) {
            error("Imported data is authoritative")
        })
        assertEquals(emptyMap(), financialProgressPayments(rows, mapOf("KH08_07" to 1L), 2024) {
            error("An empty selected year must not use legacy data")
        })
    }
}
