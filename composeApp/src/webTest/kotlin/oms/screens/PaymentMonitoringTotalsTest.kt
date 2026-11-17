package oms.screens

import oms.data.ApiPaymentMonitoringItem
import oms.data.ApiPaymentMonitoringTransaction
import kotlin.test.*

class PaymentMonitoringTotalsTest {
    @Test fun sumsEveryAbsoluteColumnAndAveragesPercentagesRatherThanRatios() {
        val first = ApiPaymentMonitoringItem("A", 10.10, 8.0, 2.10, 4.0, 3.0, 7.10, 80.0, 20.0)
        val second = ApiPaymentMonitoringItem("B", 20.20, 9.0, 11.20, 6.0, 5.0, 15.20, 40.0, 0.0)
        val result = paymentMonitoringTotals(listOf(first, second))
        assertEquals(30.30, result.totalInvestmentUah)
        assertEquals(17.0, result.eibInvestmentUah)
        assertEquals(13.30, result.localCoFinancingUah)
        assertEquals(10.0, result.paidAndInProgressUah)
        assertEquals(8.0, result.paidUah)
        assertEquals(22.30, result.remainingUah)
        assertEquals(60.0, result.plannedEibSharePct)
        assertEquals(10.0, result.factualEibSharePct)
        assertNull(result.eibShareCompliance)
    }

    @Test fun blanksAreExcludedButRealZeroParticipates() {
        val result = paymentMonitoringTotals(listOf(
            ApiPaymentMonitoringItem("A", paidUah = 0.1, plannedEibSharePct = 60.0),
            ApiPaymentMonitoringItem("B", paidUah = 0.2, plannedEibSharePct = 0.0),
            ApiPaymentMonitoringItem("C")))
        assertEquals(0.3, result.paidUah)
        assertEquals(30.0, result.plannedEibSharePct)
        assertNull(result.factualEibSharePct)
        assertNull(result.totalInvestmentUah)
    }

    @Test fun filteredTotalsIncludeEveryPageAndIgnoreSortOrder() {
        val rows = (1..25).map { ApiPaymentMonitoringItem("A$it", paidUah = it.toDouble()) } +
            ApiPaymentMonitoringItem("B", paidUah = 999.0)
        val filtered = rows.filter { it.code.startsWith("A") }
        val totals = paymentMonitoringTotals(filtered)
        assertEquals(325.0, totals.paidUah)
        assertNotEquals(totals.paidUah, paymentMonitoringTotals(filtered.take(20)).paidUah)
        assertEquals(totals, paymentMonitoringTotals(filtered.reversed()))
        assertEquals(3.0, paymentMonitoringTotals(filtered.take(2)).paidUah)
    }

    @Test fun transactionsSumEachCurrencySeparatelyAndRetainRefunds() {
        val rows = listOf(
            ApiPaymentMonitoringTransaction("1", "A_W", 8, "2026-01-01", 10.1, 1.0),
            ApiPaymentMonitoringTransaction("2", "A_TS", 8, "2026-02-01", -0.1, null))
        assertEquals(PaymentTransactionTotals(10.0, 1.0), paymentTransactionTotals(rows))
        assertEquals(PaymentTransactionTotals(-0.1, null), paymentTransactionTotals(rows.filter { it.paymentDate.startsWith("2026-02") }))
    }

    @Test fun invalidNumbersAndOverflowNeverProduceMisleadingTotals() {
        val result = paymentMonitoringTotals(listOf(ApiPaymentMonitoringItem("A",
            paidUah = Double.NaN, totalInvestmentUah = Double.MAX_VALUE, plannedEibSharePct = Double.POSITIVE_INFINITY)))
        assertNull(result.paidUah)
        assertNull(result.totalInvestmentUah)
        assertNull(result.plannedEibSharePct)
        assertNull(paymentMonitoringTotals(List(2) { ApiPaymentMonitoringItem("$it", paidUah = 6e16) }).paidUah)
    }

    @Test fun emptySelectionHasZeroSumsAndNoInventedPercentage() {
        val result = paymentMonitoringTotals(emptyList())
        assertEquals(0.0, result.paidUah)
        assertNull(result.plannedEibSharePct)
        assertEquals(PaymentTransactionTotals(0.0, 0.0), paymentTransactionTotals(emptyList()))
    }
}
