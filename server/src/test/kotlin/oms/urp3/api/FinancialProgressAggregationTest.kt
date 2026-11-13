package oms.urp3.api

import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialProgressAggregationTest {
    @Test fun `bilingual region aggregation conserves both payments and the financial base`() {
        val rows = listOf("Kharkiv" to (100.0 to 10.0), "Харківська область" to (200.0 to 40.0),
            "Kyiv Region" to (300.0 to 60.0))
        val groups = rows.groupBy { canonicalFinancialRegion(it.first) }
        assertEquals(2, groups.size)
        val kharkiv = groups.getValue(canonicalFinancialRegion("Kharkiv Region"))
        assertEquals(300.0, kharkiv.sumOf { it.second.first })
        assertEquals(50.0, kharkiv.sumOf { it.second.second })
        assertEquals(600.0, groups.values.sumOf { group -> group.sumOf { it.second.first } })
        assertEquals(110.0, groups.values.sumOf { group -> group.sumOf { it.second.second } })
        assertEquals(canonicalFinancialRegion("Харківська обл."), canonicalFinancialRegion("Kharkiv"))
        assertEquals("Unspecified", canonicalFinancialRegion(""))
    }

    @Test
    fun `sector aliases share one canonical category`() {
        assertEquals("sewerage", canonicalFinancialSector("Водовідведення / Sewerage"))
        assertEquals("sewerage", canonicalFinancialSector("Sewerage"))
        assertEquals("water_supply", canonicalFinancialSector("water_supply"))
        listOf("Укриття", "shelter", "Shelters: Other public buildings", "Укриття / Shelters: Schools").forEach {
            assertEquals("shelter", canonicalFinancialSector(it))
        }
        listOf("Security Centres", "Центри безпеки", "Центри безпеки / Security Centres").forEach {
            assertEquals("security_centres", canonicalFinancialSector(it))
        }
    }

    @Test
    fun `construction aliases merge without mixing overhaul with reconstruction`() {
        listOf("renovation", "Реконструкція", "Reconstruction/ Реконструкція").forEach {
            assertEquals("reconstruction", canonicalFinancialConstructionType(it))
        }
        assertEquals("capital_repair", canonicalFinancialConstructionType("Overhaul/ Капітальний ремонт"))
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

    @Test
    fun `works denominator excludes service contracts and preserves cents`() {
        assertEquals(100.25, financialProgressBase(100.25, 150.0, 200.0))
        assertEquals(150.0, financialProgressBase(null, 150.0, 200.0))
        assertEquals(200.0, financialProgressBase(Double.NaN, 0.0, 200.0))
        assertEquals(0.0, financialProgressBase(-1.0, null, 0.0))
    }
}
