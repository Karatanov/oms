package oms.urp3

import oms.screens.financialChartSourceRows
import oms.screens.shouldLoadLegacyFinancialRecords
import oms.screens.approvedPaymentChartRecord
import oms.data.ApiPaymentMonitoringTransaction
import oms.data.displayAmountCents
import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialChartSourceTest {
    @Test fun approvedCurrenciesAreReadDirectlyWithoutRatesOrReverseConversion() {
        val source = ApiPaymentMonitoringTransaction("a", "ZH08_04#Lot3_TS", 8, "2026-06-02", 13032.34, 263.28)
        val record = approvedPaymentChartRecord(source)
        assertEquals("technical_supervision", record.paymentPurpose)
        assertEquals(1303234L, record.displayAmountCents("UAH"))
        assertEquals(26328L, record.displayAmountCents("EUR"))
        assertEquals(1303234L, record.displayAmountCents("UAH", 999.0))
        assertEquals(0L, approvedPaymentChartRecord(source.copy(amountEur = 0.0)).displayAmountCents("EUR"))
        assertEquals(1303234L, approvedPaymentChartRecord(source.copy(amountEur = 0.0)).displayAmountCents("UAH"))
    }
    @Test fun legacyLoadingRequiresBothAnUnavailableSourceAndProjectContext() {
        for (available in listOf(false, true)) for (pending in listOf(false, true)) for (ready in listOf(false, true)) {
            assertEquals(!available && !pending && ready, shouldLoadLegacyFinancialRecords(available, pending, ready))
        }
    }

    @Test fun emptyApprovedDataNeverResurrectsOldPayments() {
        assertEquals(emptyList(), financialChartSourceRows(emptyList<Int>(), false, listOf(100)))
        assertEquals(emptyList(), financialChartSourceRows(emptyList<Int>(), true, listOf(100)))
    }

    @Test fun pendingSourceDoesNotFlashUnrelatedLegacyData() {
        assertEquals(emptyList(), financialChartSourceRows(null, true, listOf(100)))
        assertEquals(listOf(25), financialChartSourceRows(listOf(25), true, listOf(100)))
    }

    @Test fun legacyFallbackRequiresAnUnavailableSourceAndACompletedAttempt() {
        assertEquals(listOf(100), financialChartSourceRows(null, false, listOf(100)))
        assertEquals(listOf(25), financialChartSourceRows(listOf(25), false, listOf(100)))
    }
}
