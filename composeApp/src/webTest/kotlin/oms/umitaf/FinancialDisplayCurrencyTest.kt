package oms.umitaf

import kotlin.test.*
import oms.data.ApiFinancialRecord
import oms.data.displayAmountCents
import oms.screens.FinancialChartRecord
import oms.screens.aggregateMonthlyPayments
import oms.localization.*

class FinancialDisplayCurrencyTest {
    private val uah = ApiFinancialRecord("1", "payment", "P1", 4500.50, "UAH", "2026-09-01",
        eurExchangeRate = 45.0, amountEurCents = 10001)
    private val eur = ApiFinancialRecord("2", "advance", "P2", 100.25, "EUR", "2026-09-01",
        eurExchangeRate = 1.0, amountEurCents = 10025)
    @Test fun originalAndFrozenValuesArePreserved() {
        assertEquals(450050L, uah.displayAmountCents("UAH"))
        assertEquals(10001L, uah.displayAmountCents("EUR", 99.0))
        assertEquals(10025L, eur.displayAmountCents("EUR"))
        assertNull(eur.displayAmountCents("UAH")) // legacy 1.0 must not mean one hryvnia
        assertEquals(451125L, eur.displayAmountCents("UAH", 45.0))
        assertEquals(1.0, eur.eurExchangeRate)
        assertNull(eur.displayAmountCents("UAH", Double.NaN))
    }
    @Test fun chartTotalsMatchTableAmountsInBothCurrencies() {
        val rows = listOf(uah, eur).map { FinancialChartRecord(it, "KH08_07") }
        for (currency in listOf("EUR", "UAH")) {
            val chart = aggregateMonthlyPayments(rows, "works", currency, mapOf("2026-09-01" to 45.0))
            assertEquals(rows.sumOf { it.record.displayAmountCents(currency, 45.0)!! }, chart.payments.single().amountCents)
            assertEquals("2026-09", chart.payments.single().month)
            assertTrue(chart.tooltipByMonth.values.single().contains("KH08_07"))
        }
        assertTrue(aggregateMonthlyPayments(rows, "equipment").payments.isEmpty())
    }
    @Test fun missingConversionIsNeverInventedAndFallbackUsesRecordRate() {
        assertNull(eur.displayAmountCents("UAH", 0.0))
        assertEquals(10001L, uah.copy(amountEurCents = null).displayAmountCents("EUR"))
        assertNull(uah.copy(amountEurCents = null, eurExchangeRate = null).displayAmountCents("EUR"))
    }
    @Test fun messagesExistInBothLanguages() {
        val previous = LocalizationManager.currentLanguage
        try {
            for (language in listOf(Language.UK, Language.EN)) {
                LocalizationManager.currentLanguage = language
                for (key in listOf("gps_manual_hint", "gps_automatic_hint", "financial_rates_loading", "financial_rates_missing", "financial_display_currency_hint", "original_record_currency"))
                    assertNotEquals(key, LocalizationManager.t(key))
            }
        } finally { LocalizationManager.currentLanguage = previous }
    }
}
