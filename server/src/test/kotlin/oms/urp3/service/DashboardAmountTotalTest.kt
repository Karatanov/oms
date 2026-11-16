package oms.urp3.service

import java.math.BigDecimal
import kotlin.test.*

class DashboardAmountTotalTest {
    @Test fun `source cents are summed before conversion to wire double`() {
        val amounts = List(100) { BigDecimal("28569476.004") }
        assertEquals(2856947600.4, dashboardAmountTotal(amounts.asSequence()))
        assertEquals(dashboardAmountTotal(amounts.asSequence()), dashboardAmountTotal(amounts.reversed().asSequence()))
    }

    @Test fun `decimal accumulation eliminates binary addition tails`() {
        assertEquals(0.3, dashboardAmountTotal(sequenceOf(BigDecimal("0.1"), BigDecimal("0.2"))))
        assertEquals(0.0, dashboardAmountTotal(emptySequence()))
    }

    @Test fun `fractional source precision and refunds are not rounded or discarded`() {
        assertEquals(0.009, dashboardAmountTotal(sequenceOf(BigDecimal("0.005"), BigDecimal("0.004"))))
        assertEquals(0.01, dashboardAmountTotal(sequenceOf(BigDecimal("0.02"), BigDecimal("-0.01"))))
    }

    @Test fun `overflow cannot become a misleading finite subtotal`() {
        assertFailsWith<IllegalArgumentException> { dashboardAmountTotal(sequenceOf(BigDecimal("1e400"))) }
    }
}
