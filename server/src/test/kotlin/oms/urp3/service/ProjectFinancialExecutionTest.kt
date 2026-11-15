package oms.urp3.service

import java.time.LocalDate
import java.util.UUID
import kotlin.test.*
import oms.urp3.domain.*

class ProjectFinancialExecutionTest {
    private fun project(code: String) = Project(1, UUID.randomUUID(),
        if ('#' in code) ProjectType.SUBPROJECT_PART else ProjectType.SUBPROJECT, 1, null,
        "Test project", code, code, null, null, null, null, null, null, ProjectStatus.ACTIVE,
        null, null, 1000, null, null, 1000, null, null, null, null, null, null, null, "UAH", null)
    private fun payment(code: String, amount: Double) = PaymentMonitoringWorkbookSource.Transaction(
        code, code, 8, LocalDate.of(2026, 11, 15), amount, null, "test")
    private fun ledger(amount: Double, currency: String = "UAH", rate: Double? = null, purpose: String = "works") =
        FinancialRecord(1, UUID.randomUUID(), 1, FinancialRecordType.PAYMENT, "test", amount, currency,
            LocalDate.of(2026, 11, 15), null, null, null, purpose, rate)

    @Test fun `part receives only its own payments while parent includes all its lots once`() {
        val rows = listOf(payment("KH08_07#Lot1_W", 30.25), payment("KH08_07#Lot2_W\$1", 60.50),
            payment("KH08_07_W", 10.0), payment("KH08_07#Lot1_TS", 800.0),
            payment("KH08_07#Lot1_CSC", 900.0), payment("VN08_01_W", 1000.0))
        fun paid(code: String) = projectFinancialExecution(project(code), null, approvedPayments = rows) { error("No legacy fallback") }.paidUah
        assertEquals(30.25, paid("KH08_07#Lot1"))
        assertEquals(60.50, paid("kh08_07#lot2"))
        assertEquals(0.0, paid("KH08_07#Lot3"))
        assertEquals(100.75, paid("KH08_07"))
        assertEquals(financialProgressPayments(rows, mapOf("KH08_07" to 1L), null) { error("No fallback") }[1L], paid("KH08_07"))
    }
    @Test fun `loaded empty snapshot is a genuine zero for parent and part`() {
        for (code in listOf("KH08_07", "KH08_07#Lot1")) assertEquals(0.0,
            projectFinancialExecution(project(code), null, approvedPayments = emptyList()) { error("No fallback") }.paidUah)
    }
    @Test fun `unknown legacy conversion cannot produce partial execution`() {
        val rows = listOf(ledger(100.0), ledger(200.0, "EUR", 1.0))
        assertNull(projectFinancialExecution(project("KH08_07"), null) { rows }.paidUah)
        assertEquals(10100.0, projectFinancialExecution(project("KH08_07"), null) {
            rows.map { if (it.currency == "EUR") it.copy(eurExchangeRate = 50.0) else it }
        }.paidUah)
    }
    @Test fun `legacy sum preserves decimal rounding and does not wrap Long`() {
        assertEquals(140.43, projectFinancialExecution(project("KH08_07"), null) {
            listOf(ledger(100.0), ledger(0.84, "EUR", 48.125), ledger(800.0, "EUR", 1.0, "technical_supervision"))
        }.paidUah)
        assertEquals(100000000000000000.0, projectFinancialExecution(project("KH08_07"), null) {
            listOf(ledger(5e16), ledger(5e16))
        }.paidUah)
    }
}
