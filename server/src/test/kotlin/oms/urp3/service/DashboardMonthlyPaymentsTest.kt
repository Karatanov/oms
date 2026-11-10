package oms.urp3.service

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardMonthlyPaymentsTest {
    private fun row(date: String, eur: Long?, uah: Long?, batch: Int? = 8) =
        DashboardPaymentAmount(LocalDate.parse(date), eur, uah, batch)

    @Test fun `months are chronological and gaps are genuine zeroes`() {
        val result = dashboardMonthlyPayments(listOf(row("2026-02-03", 200, 10000), row("2025-12-31", 100, 5000)))
        assertEquals(listOf("2025-12", "2026-01", "2026-02"), result.map { it.month })
        assertEquals(0L, result[1].amountUahCents)
        assertEquals(300L, result.sumOf { it.amountEurCents })
    }
    @Test fun `missing EUR never discards known hryvnia payments`() {
        val result = dashboardMonthlyPayments(listOf(row("2026-02-01", null, 5000), row("2026-02-02", 200, 10000))).single()
        assertEquals(15000L, result.amountUahCents)
        assertFalse(result.hasCompleteEurAmounts)
    }
    @Test fun `missing UAH does not invent a converted zero`() {
        val result = dashboardMonthlyPayments(listOf(row("2026-02-01", 200, null))).single()
        assertNull(result.amountUahCents)
        assertTrue(result.hasCompleteEurAmounts)
    }
    @Test fun `tranche aliases and empty scope preserve source filtering`() {
        val rows = listOf(row("2026-02-01", 100, 5000, 8), row("2026-02-01", 200, 10000, 9))
        assertEquals(dashboardMonthlyPayments(rows, 1), dashboardMonthlyPayments(rows, 8))
        assertEquals(100L, dashboardMonthlyPayments(rows, 1).single().amountEurCents)
        assertEquals(200L, dashboardMonthlyPayments(rows, 2).single().amountEurCents)
        assertTrue(dashboardMonthlyPayments(rows.take(1), 2).isEmpty())
    }
}
