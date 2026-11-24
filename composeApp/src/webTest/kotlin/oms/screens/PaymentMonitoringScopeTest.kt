package oms.screens

import oms.data.ApiPaymentMonitoringItem
import oms.data.ApiPaymentMonitoringTransaction
import kotlin.test.*

class PaymentMonitoringScopeTest {
    private val projects = listOf(PaymentProjectContext("kh", "KH08_07", 1, "Харківська область"),
        PaymentProjectContext("vn", "VN09_01", 2, "Вінницька область"))
    private fun payment(code: String, date: String = "2026-10-01", batch: Int = 8) =
        ApiPaymentMonitoringTransaction(code, code, batch, date, 100.25, 2.0)

    @Test fun chartPurposeMonthRegionAndTrancheSelectExactlyTheAuditableRows() {
        val source = listOf(payment("KH08_07#Lot1_W"), payment("KH08_07_TS\$1"),
            payment("KH08_07_TS", "2026-09-01"), payment("VN09_01_TS", batch = 9))
        val scope = PaymentMonitoringScope(1, "kh", "Харківська", 2026, "2026-10", "technical_supervision")
        assertEquals(listOf(source[1]), scopePaymentTransactions(source, projects, scope))
        assertEquals(4, source.size)
        assertEquals(100.25, source[1].amountUah)
    }

    @Test fun trancheAliasesAndUnfilteredResetDoNotLoseUnknownProjects() {
        val source = listOf(payment("UNKNOWN_W", batch = 9), payment("KH08_07_W"))
        assertEquals(listOf(source[0]), scopePaymentTransactions(source, projects, PaymentMonitoringScope(tranche = 2)))
        assertEquals(source, scopePaymentTransactions(source, projects, PaymentMonitoringScope()))
        assertEquals(emptyList(), scopePaymentTransactions(source, projects, PaymentMonitoringScope(subproject = "missing")))
    }

    @Test fun cumulativeRowsAreScopedByProjectNotByTransactionMonthOrPurpose() {
        val source = listOf(ApiPaymentMonitoringItem("KH08_07"), ApiPaymentMonitoringItem("KH08_07#Lot1_W"),
            ApiPaymentMonitoringItem("VN09_01"))
        val scope = PaymentMonitoringScope(tranche = 1, month = "2026-10", purpose = "technical_supervision")
        assertEquals(source.take(2), scopePaymentItems(source, projects, scope))
    }

    @Test fun unknownContractTypesAreNotSilentlyClassifiedAsWorks() {
        assertNull(sourcePaymentPurpose("KH08_07_UNSUPPORTED"))
        assertEquals("engineer_consultant", sourcePaymentPurpose("KH08_07_CSC\$1"))
    }

    @Test fun headlineUsesDashboardTotalOnlyWithoutFilters() {
        val rows = listOf(payment("KH08_07_W"), payment("VN09_01_TS", batch = 9))
        assertEquals(999.99, scopedPaymentTotalUah(rows, projects, PaymentMonitoringScope(), 999.99))
        assertEquals(100.25, scopedPaymentTotalUah(rows, projects, PaymentMonitoringScope(subproject = "kh"), 999.99))
        assertNull(scopedPaymentTotalUah(null, projects, PaymentMonitoringScope(subproject = "kh"), 999.99))
    }

    @Test fun projectAndMonthIntersectAndAllPurposeCardsUseTheSamePeriod() {
        val rows = listOf(payment("KH08_07#Lot1_W"), payment("KH08_07_TS\$1"), payment("KH08_07_CSC"),
            payment("KH08_07_W", "2026-09-01"), payment("VN09_01_W", batch = 9))
        val scope = PaymentMonitoringScope(subproject = "kh", month = "2026-10")
        assertEquals(300.75, scopedPaymentTotalUah(rows, projects, scope, 999.99))
        for (purpose in listOf("works", "technical_supervision", "engineer_consultant")) {
            assertEquals(100.25, scopedPaymentTotalUah(rows, projects, scope.copy(purpose = purpose), 999.99))
        }
        assertEquals(0.0, scopedPaymentTotalUah(rows, projects, scope.copy(month = "2026-08"), 999.99))
        assertEquals(401.0, scopedPaymentTotalUah(rows, projects, PaymentMonitoringScope(month = "2026-10"), 999.99))
    }

    @Test fun paymentTotalsUseMinorUnitsAndDoNotTurnInvalidAmountsIntoZero() {
        assertEquals(0.3, paymentAmountTotalUah(listOf(payment("KH08_07_W").copy(amountUah = 0.1), payment("KH08_07_TS").copy(amountUah = 0.2))))
        assertNull(paymentAmountTotalUah(listOf(payment("KH08_07_W").copy(amountUah = Double.NaN))))
    }

    @Test fun juneSupervisionLotsResolveToTheirParentWithoutRepeatingParentSummary() {
        val context = listOf(PaymentProjectContext("zh", "ZH08_04", 1, "Житомирська область"),
            PaymentProjectContext("ck", "CK08_05", 1, "Черкаська область"))
        val rows = listOf(
            payment("ZH08_04#Lot3_TS", "2026-06-02").copy(amountUah = 13032.34, amountEur = 263.28),
            payment("ZH08_04#Lot4_TS", "2026-06-03").copy(amountUah = 13651.67, amountEur = 275.79),
            payment("CK08_05_TS", "2026-06-16").copy(amountUah = 9536.81, amountEur = 192.66)
        )
        // This is also the project-key lookup used to construct monthly chart rows.
        val chartRows = rows.filter { row -> context.any { it.code == oms.urp3.paymentSubprojectKey(row.code) } }
        assertEquals(3, chartRows.size)
        val scope = PaymentMonitoringScope(month = "2026-06", purpose = "technical_supervision")
        assertEquals(36220.82, paymentAmountTotalUah(scopePaymentTransactions(chartRows, context, scope)))
        assertEquals(scopedPaymentTotalUah(rows, context, scope, null), paymentAmountTotalUah(chartRows))
        assertEquals(26684.01, scopedPaymentTotalUah(rows, context, scope.copy(subproject = "zh"), null))
        assertEquals(731.73, chartRows.sumOf { oms.urp3.decimalMoneyCents(it.amountEur!!)!! } / 100.0)
        assertEquals("ZH08_04", oms.urp3.paymentSubprojectKey("ZH08_04#Lot3_TS\$1"))
        assertEquals("ZH08_04", oms.urp3.paymentSubprojectKey("ZH08_04#Lot3_W"))
        assertEquals("ZH08_04", oms.urp3.paymentSubprojectKey("ZH08_04#Lot3_CSC"))
        assertEquals("ZH08_04", oms.urp3.paymentSubprojectKey("ZH08_04_E"))
        assertEquals("ZH08_04", oms.urp3.paymentSubprojectKey("ZH08_04#Lot3_EQUIPMENT"))
        assertEquals("ZH08_04", oms.urp3.paymentSubprojectKey("ZH08_04"))
    }
}
