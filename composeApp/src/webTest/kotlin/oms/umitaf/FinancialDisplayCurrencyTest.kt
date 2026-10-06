package oms.umitaf

import kotlin.test.*
import oms.data.ApiFinancialRecord
import oms.data.displayAmountCents
import oms.screens.FinancialChartRecord
import oms.screens.aggregateMonthlyPayments
import oms.screens.completeFinancialMonths
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
        assertEquals(451125L, eur.copy(eurExchangeRate = 45.0).displayAmountCents("UAH"))
        assertEquals(1.0, eur.eurExchangeRate)
        assertNull(eur.displayAmountCents("UAH", Double.NaN))
    }
    @Test fun chartTotalsMatchTableAmountsInBothCurrencies() {
        val rows = listOf(uah, eur).map { FinancialChartRecord(it, "KH08_07") }
        for (currency in listOf("EUR", "UAH")) {
            val chart = aggregateMonthlyPayments(rows, "works", currency, mapOf("2026-09-01" to 45.0))
            val september = chart.payments.first { it.month == "2026-09" }
            assertEquals(rows.sumOf { it.record.displayAmountCents(currency, 45.0)!! }, september.amountCents)
            assertEquals("2026-09", chart.payments.first().month)
            assertTrue(chart.tooltipByMonth.values.single().contains("KH08_07"))
        }
        assertTrue(aggregateMonthlyPayments(rows, "equipment").payments.isEmpty())
    }
    @Test fun engineerConsultantPaymentsUseTheirOwnPurpose() {
        val engineerPayment = uah.copy(uuid = "csc", paymentPurpose = "engineer_consultant")
        val technicalPayment = uah.copy(uuid = "ts", paymentPurpose = "technical_supervision")
        val rows = listOf(engineerPayment, technicalPayment).map { FinancialChartRecord(it, "KH08_07") }

        val chart = aggregateMonthlyPayments(rows, "engineer_consultant", "EUR", mapOf("2026-09-01" to 45.0))

        assertEquals(1, chart.payments.size)
        assertEquals(10001L, chart.payments.single().amountCents)
        assertTrue(chart.tooltipByMonth.values.single().contains("KH08_07"))
    }
    @Test fun missingConversionIsNeverInventedAndFallbackUsesRecordRate() {
        assertNull(eur.displayAmountCents("UAH", 0.0))
        assertEquals(10001L, uah.copy(amountEurCents = null).displayAmountCents("EUR"))
        assertNull(uah.copy(amountEurCents = null, eurExchangeRate = null).displayAmountCents("EUR"))
    }
    @Test fun monthlyChartsStartWithFirstPaymentAndIncludeLaterCalendarGaps() {
        val completed = completeFinancialMonths(listOf(
            oms.screens.MonthlyMoneyAmount("2026-02", 1250),
            oms.screens.MonthlyMoneyAmount("2026-04", 3750)
        ))
        assertEquals(listOf("2026-02", "2026-03", "2026-04"), completed.map { it.month })
        assertEquals(listOf(1250L, 0L, 3750L), completed.map { it.amountCents })
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
