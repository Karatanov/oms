package oms.screens

import oms.data.ApiPaymentMonitoringItem
import kotlin.test.*

class RegionalInvestmentTest {
    private val projects = listOf(PaymentProjectContext("kh", "KH08_01", 1, "Харківська"), PaymentProjectContext("lv", "LV09_01", 2, "Львівська"))
    private fun item(code: String, total: Double? = 100.0, eib: Double? = 80.0, local: Double? = 20.0) =
        ApiPaymentMonitoringItem(code, totalInvestmentUah = total, eibInvestmentUah = eib, localCoFinancingUah = local)

    @Test fun investmentsAreNotPaymentsAndParentBudgetsDoNotRepeatLotSubtotals() {
        val source = listOf(item("KH08_01"), item("KH08_01#Lot1", 60.0, 48.0, 12.0), item("KH08_01#Lot2", 40.0, 32.0, 8.0))
        val result = regionalInvestment(source, projects, PaymentMonitoringScope()).single()
        assertEquals(10000L, result.total); assertEquals(8000L, result.eib); assertEquals(2000L, result.local)
        assertTrue(result.reconciled)
    }

    @Test fun lotsContributeWhenTheirParentBudgetIsMissing() {
        val source = listOf(item("KH08_01", null, null, null), item("KH08_01#Lot1", 60.0, 48.0, 12.0), item("KH08_01#Lot2", 40.0, 32.0, 8.0))
        assertEquals(10000L, regionalInvestment(source, projects, PaymentMonitoringScope()).single().total)
    }

    @Test fun scopeSelectsSubprojectRegionAndTrancheButDoesNotTurnBudgetsIntoMonthlyCash() {
        val source = listOf(item("KH08_01"), item("LV09_01", 200.0, 150.0, 50.0))
        assertEquals(2, regionalInvestment(source, projects, PaymentMonitoringScope()).size)
        assertEquals(10000L, regionalInvestment(source, projects, PaymentMonitoringScope(subproject = "kh", month = "2026-06", purpose = "technical_supervision")).single().total)
        assertEquals(20000L, regionalInvestment(source, projects, PaymentMonitoringScope(tranche = 9)).single().total)
        assertEquals(10000L, regionalInvestment(source, projects, PaymentMonitoringScope(region = "Харківська область")).single().total)
    }

    @Test fun sourceDifferencesAndMissingValuesAreNotRepairedWithInventedAmounts() {
        val result = regionalInvestment(listOf(item("KH08_01", 100.0, 70.0, 20.0)), projects, PaymentMonitoringScope()).single()
        assertFalse(result.reconciled); assertEquals(10000L, result.total)
        assertNull(regionalInvestment(listOf(item("KH08_01", null)), projects, PaymentMonitoringScope()).single().total)
        assertEquals(0L, regionalInvestment(listOf(item("KH08_01", 0.0, 0.0, 0.0)), projects, PaymentMonitoringScope()).single().total)
    }
}
