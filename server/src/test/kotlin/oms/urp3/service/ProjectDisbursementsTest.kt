package oms.urp3.service

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

class ProjectDisbursementsTest {
    private val nodes = listOf(DisbursementNode(1, null, "URP-III"), DisbursementNode(2, 1, "KH08_07"),
        DisbursementNode(3, 2, "KH08_07#Lot1"), DisbursementNode(4, 2, "KH08_07#Lot2"), DisbursementNode(5, 1, "VN08_01"))
    private fun payment(code: String, uah: Double, eur: Double?) =
        PaymentMonitoringWorkbookSource.Transaction(code, code, 8, LocalDate.of(2026, 4, 24), uah, eur, "test")
    @Test fun `lot payments roll up once while services do not inflate Works UAH`() {
        val rows = listOf(payment("KH08_07#Lot1_W", 500.25, 10.01), payment("KH08_07#Lot2_W\$1", 250.0, 5.0),
            payment("KH08_07_TS", 50.0, 1.0), payment("VN08_01_W", 1000.0, 20.0))
        val result = projectDisbursements(nodes, rows) { error("Do not read legacy") }
        assertEquals(1601L, result[2]?.eurCents)
        assertEquals(BigDecimal("750.25"), result[2]?.worksUah)
        assertEquals(1001L, result[3]?.eurCents)
        assertEquals(500L, result[4]?.eurCents)
        assertEquals(3601L, result[1]?.eurCents)
    }
    @Test fun `empty imported snapshot is genuine zero not legacy fallback`() {
        assertTrue(projectDisbursements(nodes, emptyList()) { error("Do not resurrect old cash") }.isEmpty())
    }
    @Test fun `legacy is used only when source is unavailable and keeps project attribution`() {
        var calls = 0
        val result = projectDisbursements(nodes, null) {
            calls++
            listOf(DisbursementPayment("", 123L, BigDecimal("50.00"), true, 3))
        }
        assertEquals(1, calls)
        assertEquals(123L, result[2]?.eurCents)
        assertEquals(123L, result[3]?.eurCents)
        assertNull(result[5])
    }
    @Test fun `unknown EUR is unavailable rather than partial or invented zero`() {
        val result = projectDisbursements(nodes, listOf(payment("KH08_07#Lot1_W", 100.0, null),
            payment("KH08_07_TS", 50.0, 1.0))) { error("legacy") }
        assertNull(result[2]?.eurCents)
        assertEquals(0, result[2]?.worksUah?.compareTo(BigDecimal("100")))
    }
    @Test fun `unknown lot attributes to its known parent not a different lot`() {
        val result = projectDisbursements(nodes, listOf(payment("KH08_07#Lot99_W", 100.0, 2.0))) { error("legacy") }
        assertEquals(200L, result[2]?.eurCents)
        assertNull(result[3])
        assertNull(result[4])
    }
}
