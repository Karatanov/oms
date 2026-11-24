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
}
