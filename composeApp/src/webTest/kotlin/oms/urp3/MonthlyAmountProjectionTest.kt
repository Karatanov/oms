package oms.urp3

import oms.data.ApiMonthlyActPayment
import oms.screens.projectMonthlyAmounts
import kotlin.test.*

class MonthlyAmountProjectionTest {
    @Test fun missingUahDoesNotBecomeAZeroOrAnOmittedMonth() {
        val source = listOf(ApiMonthlyActPayment("2026-02", 100, null), ApiMonthlyActPayment("2026-04", 200, 10000))
        val result = projectMonthlyAmounts(source, "UAH")
        assertEquals(1, result.unavailableMonths)
        assertTrue(result.payments.isEmpty())
        assertEquals(listOf(100L, 200L), projectMonthlyAmounts(source, "EUR").payments.map { it.amountCents })
    }

    @Test fun eurCompletenessFlagWinsOverItsNonNullWirePlaceholder() {
        val source = listOf(ApiMonthlyActPayment("2026-02", 123, 5000, false), ApiMonthlyActPayment("2026-03", 100, 5000))
        assertTrue(projectMonthlyAmounts(source, "EUR").payments.isEmpty())
        assertEquals(1, projectMonthlyAmounts(source, "EUR").unavailableMonths)
        assertEquals(listOf(5000L, 5000L), projectMonthlyAmounts(source, "UAH").payments.map { it.amountCents })
    }

    @Test fun genuineGapZeroAndCorrectionsRemainVisibleInChronologicalOrder() {
        val source = listOf(ApiMonthlyActPayment("2026-04", -100, -5000),
            ApiMonthlyActPayment("2026-02", 100, 5000), ApiMonthlyActPayment("2026-03", 0, 0))
        val original = source.toList()
        val result = projectMonthlyAmounts(source, "UAH")
        assertEquals(0, result.unavailableMonths)
        assertEquals(listOf("2026-02", "2026-03", "2026-04"), result.payments.map { it.month })
        assertEquals(listOf(5000L, 0L, -5000L), result.payments.map { it.amountCents })
        assertEquals(original, source)
    }

    @Test fun refreshedCompleteValuesRestoreChartWithoutChangingOtherCurrency() {
        val row = ApiMonthlyActPayment("2026-02", 100, null)
        assertTrue(projectMonthlyAmounts(listOf(row), "UAH").payments.isEmpty())
        assertEquals(5000L, projectMonthlyAmounts(listOf(row.copy(amountUahCents = 5000)), "UAH").payments.single().amountCents)
        assertEquals(100L, projectMonthlyAmounts(listOf(row), "EUR").payments.single().amountCents)
    }

    @Test fun emptySourceIsNotAnErrorAndUnsupportedCurrencyCannotMasqueradeAsUah() {
        assertEquals(0, projectMonthlyAmounts(emptyList(), "UAH").unavailableMonths)
        val result = projectMonthlyAmounts(listOf(ApiMonthlyActPayment("2026-02", 100, 5000)), "USD")
        assertEquals(1, result.unavailableMonths)
        assertTrue(result.payments.isEmpty())
    }
}
