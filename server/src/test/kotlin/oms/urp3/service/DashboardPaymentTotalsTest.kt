package oms.urp3.service

import java.math.BigDecimal
import kotlin.test.*

class DashboardPaymentTotalsTest {
    @Test fun `missing EUR does not expose a partial EUR subtotal or poison known UAH`() {
        assertEquals(DashboardPaymentTotals(null, 300L), dashboardPaymentTotals(listOf(10L to 100L, null to 200L)))
    }

    @Test fun `native EUR remains available without a saved EUR equivalent or a valid UAH rate`() {
        val native = BigDecimal("100.25")
        val eur = frozenFinancialCents(native, "EUR", "EUR", null, BigDecimal.ONE)
        val uah = frozenFinancialCents(native, "EUR", "UAH", null, BigDecimal.ONE)
        assertEquals(DashboardPaymentTotals(10025L, null), dashboardPaymentTotals(listOf(eur to uah)))
    }

    @Test fun `empty scope and explicit zero are genuine zeros`() {
        assertEquals(DashboardPaymentTotals(0L, 0L), dashboardPaymentTotals(emptyList()))
        assertEquals(DashboardPaymentTotals(0L, 0L), dashboardPaymentTotals(listOf(0L to 0L)))
    }

    @Test fun `overflow and underflow are unavailable independently in each currency`() {
        assertEquals(DashboardPaymentTotals(null, 3L), dashboardPaymentTotals(listOf(Long.MAX_VALUE to 1L, 1L to 2L)))
        assertEquals(DashboardPaymentTotals(3L, null), dashboardPaymentTotals(listOf(1L to Long.MIN_VALUE, 2L to -1L)))
    }

    @Test fun `negative corrections preserve final cents regardless of record order`() {
        val rows = listOf(Long.MAX_VALUE to 10L, 1L to -2L, -1L to 3L)
        assertEquals(DashboardPaymentTotals(Long.MAX_VALUE, 11L), dashboardPaymentTotals(rows))
        assertEquals(dashboardPaymentTotals(rows), dashboardPaymentTotals(rows.reversed()))
    }
}
