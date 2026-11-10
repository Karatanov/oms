package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMonitoringTotalsTest {
    private fun item(code: String, paid: Double, factualShare: Double? = null) =
        PaymentMonitoringWorkbookSource.Item(code, null, null, null, null, paid,
            null, null, factualShare, null)

    @Test fun `paid amount is not multiplied by execution ratio or dependent on its presence`() {
        val rows = listOf(item("VN08_01", 100.0, 10.0), item("VN08_02", 200.0))
        assertEquals(300.0, primaryMonitoringPaidUah(rows, setOf("VN08_01", "VN08_02")))
    }

    @Test fun `components and out of scope subprojects never inflate the total`() {
        val rows = listOf(item("VN08_01", 100.0), item("VN08_01_W", 80.0),
            item("VN08_01_TS", 20.0), item("VN08_02", 200.0))
        assertEquals(100.0, primaryMonitoringPaidUah(rows, setOf("VN08_01")))
        assertEquals(0.0, primaryMonitoringPaidUah(rows, emptySet()))
    }
}
