package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SectorClassificationTest {
    @Test fun broadWaterSectorHasItsOwnCanonicalKeyRatherThanGuessingOneComponent() {
        for (label in listOf("Water supply & Sewerage", "Water supply and sewerage", "Water and sewerage", "Водопостачання та водовідведення")) {
            assertEquals("water_supply_sewerage", financialSectorKey(label))
            assertNotEquals("water_supply", financialSectorKey(label))
            assertNotEquals("sewerage", financialSectorKey(label))
        }
    }
    @Test fun allocationBroadCaptionPreservesEitherKnownDetailedSector() {
        for (precise in listOf("Водовідведення / Sewerage", "Water supply", "sewerage", "water_supply")) {
            assertEquals(precise, preferredSourceSector("Water supply & Sewerage", precise))
            assertEquals(precise, preferredSourceSector("Water supply & Sewerage",
                preferredSourceSector("Water supply & Sewerage", precise)))
        }
    }
    @Test fun specificIncomingValuesRemainAuthoritativeAndUnresolvedBroadValuesRemainBroad() {
        assertEquals("Sewerage", preferredSourceSector("Sewerage", "Water supply"))
        assertEquals("Water supply & Sewerage", preferredSourceSector("Water supply & Sewerage", ""))
        assertEquals("Water supply & Sewerage", preferredSourceSector("Water supply & Sewerage", "Education"))
        assertEquals("Sewerage", preferredSourceSector("", "Sewerage"))
    }
    @Test fun mergingEquivalentLabelsDoesNotDuplicateOrLoseAmounts() {
        val rows = listOf("Water supply & Sewerage" to 100L, "Водопостачання та водовідведення" to 200L, "Sewerage" to 300L)
        val sums = rows.groupBy { financialSectorKey(it.first) }.mapValues { (_, values) -> values.sumOf { it.second } }
        assertEquals(mapOf("water_supply_sewerage" to 300L, "sewerage" to 300L), sums)
        assertEquals(rows.sumOf { it.second }, sums.values.sum())
    }
}
