package oms.urp3.service

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialProgressPaymentsTest {
    @Test fun `lot contract bases are summed only when a parent total is unavailable`() {
        val lots = listOf(2_867_194.45, 2_896_685.69, 2_929_089.77, null, null, 2_905_436.22)
        assertEquals(11_598_406.13, financialProgressSourceWorks(null, lots)!!, 0.001)
        assertEquals(11_598_406.13, financialProgressSourceWorks(0.0, lots)!!, 0.001)
        assertEquals(12_000_000.0, financialProgressSourceWorks(12_000_000.0, lots))
        assertEquals(null, financialProgressSourceWorks(null, listOf(null, 0.0, Double.NaN, -1.0)))
    }

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
