package oms.screens

import oms.data.ApiPaymentMonitoringTransaction
import kotlin.test.*

class RegionalFinancingTest {
    private fun project(code: String, region: String, plan: Long? = 10000, tranche: Int = 1) =
        RegionalFinancingProject(PaymentProjectContext(code, code, tranche, region), plan)
    private fun payment(code: String, amount: Double, date: String = "2026-02-01", batch: Int = 8) =
        ApiPaymentMonitoringTransaction(code, code, batch, date, amount, null)

    @Test fun lotPaymentsAndAllServicePurposesRollUpWithoutDuplicatingParentBudgets() {
        val projects = listOf(project("KH08_01", "Харківська область"), project("KH08_01#Lot1", "Харківська", 999999))
        val rows = listOf(payment("KH08_01#Lot1_W", 10.25), payment("KH08_01#Lot2_W", 20.50),
            payment("KH08_01_TS", 3.0), payment("KH08_01_CSC", 4.0), payment("KH08_01_E", 500.0))
        val result = regionalFinancing(projects, rows, PaymentMonitoringScope()).single()
        assertEquals(10000L, result.plannedCents)
        assertEquals(3775L, result.paidCents)
    }
    @Test fun periodAndPurposeRestrictCashNotTheSnapshotBudget() {
        val projects = listOf(project("KH08_01", "Харківська"))
        val rows = listOf(payment("KH08_01_W", 10.0), payment("KH08_01_TS", 3.0), payment("KH08_01_W", 20.0, "2025-02-01"))
        val result = regionalFinancing(projects, rows, PaymentMonitoringScope(year = 2026, purpose = "works")).single()
        assertEquals(10000L, result.plannedCents)
        assertEquals(1000L, result.paidCents)
    }
    @Test fun trancheAndRegionFiltersUseExistingScopeAndRegionalTotalsReconcile() {
        val projects = listOf(project("KH08_01", "Харківська", 10000), project("KH08_02", "Харківська область", 20000),
            project("LV09_01", "Львівська", 30000, 2))
        val rows = listOf(payment("KH08_01_W", 10.0), payment("KH08_02_W", 20.0), payment("LV09_01_W", 30.0, batch = 9))
        val all = regionalFinancing(projects, rows, PaymentMonitoringScope())
        assertEquals(60000L, all.sumOf { it.plannedCents!! })
        assertEquals(6000L, all.sumOf { it.paidCents!! })
        assertEquals(30000L, regionalFinancing(projects, rows, PaymentMonitoringScope(tranche = 9)).single().plannedCents)
        assertEquals(3000L, regionalFinancing(projects, rows, PaymentMonitoringScope(region = "Харківська область")).single().paidCents)
    }
    @Test fun absentSnapshotIsUnavailableButEmptyLoadedSnapshotIsRealZero() {
        val projects = listOf(project("KH08_01", "Харківська", null))
        assertNull(regionalFinancing(projects, null, PaymentMonitoringScope()).single().paidCents)
        val empty = regionalFinancing(projects, emptyList(), PaymentMonitoringScope()).single()
        assertNull(empty.plannedCents)
        assertEquals(0L, empty.paidCents)
    }
    @Test fun duplicatesUnknownCodesAndOverflowNeverCreateInflatedOrWrappedTotals() {
        val p = project("KH08_01", "Харківська")
        val result = regionalFinancing(listOf(p, p), listOf(payment("OTHER_W", 999.0)), PaymentMonitoringScope()).single()
        assertEquals(10000L, result.plannedCents)
        assertEquals(0L, result.paidCents)
        val huge = regionalFinancing(listOf(p.copy(plannedCents = Long.MAX_VALUE), project("KH08_02", "Харківська", 1)),
            listOf(payment("KH08_01_W", Double.NaN)), PaymentMonitoringScope()).single()
        assertNull(huge.plannedCents)
        assertNull(huge.paidCents)
    }
}
