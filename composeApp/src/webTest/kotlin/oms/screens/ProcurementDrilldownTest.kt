package oms.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class ProcurementDrilldownTest {
    private val stale = ProcurementTableFilters("cv08_03", "Old oblast", "CV08_03", "Old method", "Old status", "2025-01", "TS")

    @Test fun terminatedStatusReplacesConflictingSearchMonthAndColumnFilters() {
        assertEquals(ProcurementTableFilters(status = "Contract terminated"), stale.forChart("Contract terminated"))
        assertEquals("cv08_03", stale.search)
    }

    @Test fun monthlyChartClearsStaleSearchAndRetainsOnlyItsOwnSelection() {
        assertEquals(ProcurementTableFilters(status = "Contract signed", month = "2025-12"),
            stale.forChart("Contract signed", "2025-12"))
        assertEquals(ProcurementTableFilters(status = "Contract signed", month = "2025-12", contractType = "W"),
            stale.forChart("Contract signed", "2025-12", "W"))
    }
    @Test fun tooltipListsUniqueSubprojectsWithoutChangingSourceReferences() {
        val source = listOf("ZH08_04#Lot3_TS", "zh08_04#Lot4_W\$2", "CV08_03_TS", " CV08_03_W ", "")
        assertEquals(listOf("CV08_03", "ZH08_04"), procurementChartSubprojectCodes(source))
        assertEquals("ZH08_04#Lot3_TS", source.first())
    }

    @Test fun listsWrapIntoReadableLinesAndEmptyGroupsStayEmpty() {
        assertEquals("AA08_01, BB08_01, CC08_01\nDD08_01", procurementChartCodeLines(listOf("DD08_01", "CC08_01", "AA08_01", "BB08_01")))
        assertEquals("", procurementChartCodeLines(emptyList()))
    }
}
