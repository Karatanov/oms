package oms.urp3.api

import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialProgressAggregationTest {
    @Test
    fun `sector aliases share one canonical category`() {
        assertEquals("sewerage", canonicalFinancialSector("Водовідведення / Sewerage"))
        assertEquals("sewerage", canonicalFinancialSector("Sewerage"))
        assertEquals("water_supply", canonicalFinancialSector("water_supply"))
    }

    @Test
    fun `progress uses aggregated numerator and denominator rather than averaging percentages`() {
        // Two contracts receive multiple works payments.  The portfolio ratio
        // must be sum(payments)/sum(contracts), not an average of SP ratios:
        // 100/100 (100%) and (40+60)/1,000 (10%) = 200/1,100 = 18.18%, not 55%.
        val contractAmounts = listOf(100.0, 1_000.0)
        val worksPayments = listOf(100.0, 40.0, 60.0)
        assertEquals(
            18.1818181818,
            financialProgressPercentage(worksPayments.sum(), contractAmounts.sum()),
            0.0000001
        )
        assertEquals(0.0, financialProgressPercentage(200.0, 0.0))
    }
}
