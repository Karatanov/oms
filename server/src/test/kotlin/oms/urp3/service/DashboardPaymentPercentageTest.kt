package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardPaymentPercentageTest {
    @Test
    fun `calculates paid share from payment-monitoring source values`() {
        assertEquals(25.0, paymentMonitoringPaymentPct(4_000_000.0, 1_000_000.0))
    }

    @Test
    fun `retains payments above eligible value as a visible anomaly`() {
        assertEquals(125.0, paymentMonitoringPaymentPct(1_000.0, 1_250.0))
    }

    @Test
    fun `does not invent a ratio for missing or zero eligible value`() {
        assertNull(paymentMonitoringPaymentPct(null, 10.0))
        assertNull(paymentMonitoringPaymentPct(0.0, 10.0))
        assertNull(paymentMonitoringPaymentPct(1_000.0, null))
    }
}
