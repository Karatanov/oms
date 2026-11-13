package oms.urp3

import oms.screens.financialChartSourceRows
import oms.screens.shouldLoadLegacyFinancialRecords
import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialChartSourceTest {
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
