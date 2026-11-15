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
}
