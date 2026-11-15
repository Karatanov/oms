package oms.urp3

import oms.data.ApiFinancialRecord
import oms.screens.FinancialChartRecord
import oms.screens.aggregateMonthlyPayments
import oms.screens.missingChartRateDates
import oms.screens.validChartRates
import kotlin.test.*

class MonthlyFinancialCompletenessTest {
    private fun row(id: String, purpose: String = "works", date: String = "2026-04-24", rate: Double? = 50.0) =
        FinancialChartRecord(ApiFinancialRecord(id, "payment", id, 100.0, "EUR", date,
            paymentPurpose = purpose, eurExchangeRate = rate, amountEurCents = 10000), id)

    @Test fun oneUnknownConversionCannotProduceAPartialBarOrMisleadingTooltip() {
        val result = aggregateMonthlyPayments(listOf(row("known"), row("unknown", rate = null)), "works", "UAH")
        assertEquals(1, result.unavailableRecords)
        assertTrue(result.payments.isEmpty())
        assertTrue(result.tooltipByMonth.isEmpty())
    }
    @Test fun missingConversionMonthDoesNotBecomeAGapZeroAndRetryRestoresAllRows() {
        val source = listOf(row("unknown", rate = null), row("known", date = "2026-06-24"))
        assertTrue(aggregateMonthlyPayments(source, "works", "UAH").payments.isEmpty())
        val resolved = aggregateMonthlyPayments(source, "works", "UAH", mapOf("2026-04-24" to 50.0))
        assertEquals(0, resolved.unavailableRecords)
        assertEquals(listOf("2026-04", "2026-05", "2026-06"), resolved.payments.map { it.month })
        assertEquals(listOf(500000L, 0L, 500000L), resolved.payments.map { it.amountCents })
        assertNull(source.first().record.eurExchangeRate)
    }
    @Test fun anIncompletePurposeDoesNotHideOtherCompletePurposesOrOtherCurrency() {
        val source = listOf(row("W", rate = null), row("TS", purpose = "technical_supervision"))
        assertEquals(1, aggregateMonthlyPayments(source, "works", "UAH").unavailableRecords)
        assertEquals(500000L, aggregateMonthlyPayments(source, "technical_supervision", "UAH").payments.single().amountCents)
        assertEquals(10000L, aggregateMonthlyPayments(source, "works", "EUR").payments.single().amountCents)
    }
    @Test fun missingRateLookupUsesBothDirectionsAndNeverRefetchesFrozenConversions() {
        val eur = row("EUR", rate = 1.0)
        val uah = row("UAH").let { it.copy(record = it.record.copy(currency = "UAH", eurExchangeRate = null, amountEurCents = null)) }
        assertEquals(listOf("2026-04-24"), missingChartRateDates(listOf(eur), "works", "UAH", emptyMap()))
        assertEquals(listOf("2026-04-24"), missingChartRateDates(listOf(eur, eur), "works", "UAH", emptyMap()))
        assertEquals(listOf("2026-04-24"), missingChartRateDates(listOf(uah), "works", "EUR", emptyMap()))
        assertTrue(missingChartRateDates(listOf(uah.copy(record = uah.record.copy(amountEurCents = 0))), "works", "EUR", emptyMap()).isEmpty())
        assertTrue(missingChartRateDates(listOf(eur), "technical_supervision", "UAH", emptyMap()).isEmpty())
        assertTrue(missingChartRateDates(listOf(eur, eur), "works", "UAH", mapOf("2026-04-24" to 50.0)).isEmpty())
    }
    @Test fun suppliedValidRatesWinButInvalidValuesDoNotPoisonTheCache() {
        assertEquals(mapOf("date" to 49.0), validChartRates(mapOf("date" to 48.0), mapOf("date" to 49.0)))
        assertEquals(mapOf("date" to 48.0), validChartRates(mapOf("date" to 48.0), mapOf("date" to Double.NaN)))
        assertTrue(validChartRates(mapOf("a" to 0.0), mapOf("b" to -1.0, "c" to Double.POSITIVE_INFINITY)).isEmpty())
    }
    @Test fun badDatesAndOverflowStayUnavailableInsteadOfDisappearingOrWrapping() {
        for (date in listOf("2026-13-01", "2026-02-29", "2026-04-31", "bad")) {
            assertEquals(1, aggregateMonthlyPayments(listOf(row("bad", date = date))).unavailableRecords)
        }
        assertEquals(0, aggregateMonthlyPayments(listOf(row("leap", date = "2024-02-29"))).unavailableRecords)
        val huge = row("huge").let { it.copy(record = it.record.copy(amount = 5e16)) }
        val result = aggregateMonthlyPayments(listOf(huge, huge))
        assertEquals(2, result.unavailableRecords)
        assertTrue(result.payments.isEmpty())
    }
}
