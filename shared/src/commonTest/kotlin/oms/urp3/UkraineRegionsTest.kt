package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UkraineRegionsTest {
    @Test fun allRegionNamesResolveToTheSameIdentityInBothLanguages() {
        assertEquals(25, ukraineRegionNames.size)
        ukraineRegionNames.forEach { region ->
            listOf(region.ukrainianName, region.englishName, region.englishName.removeSuffix(" Region"),
                region.ukrainianName.removeSuffix(" область"), "  ${region.englishName.uppercase()}  ")
                .forEach { assertEquals(region.ukrainianName, canonicalUkraineRegion(it), it) }
        }
    }

    @Test fun abbreviationsAndNonbreakingSpacesDoNotSplitARegion() {
        assertEquals("Харківська область", canonicalUkraineRegion("Харківська обл."))
        assertEquals("Харківська область", canonicalUkraineRegion("Kharkiv\u00a0Region"))
        assertEquals("Харківська область", canonicalUkraineRegion("Kharkiv oblast"))
        assertEquals("", canonicalUkraineRegion("  "))
        assertEquals("Custom value", canonicalUkraineRegion("Custom   value"))
    }

    @Test fun cityOfKyivIsNotMergedWithKyivRegion() {
        assertEquals("місто Київ", canonicalUkraineRegion("Kyiv City"))
        assertNotEquals(canonicalUkraineRegion("Kyiv City"), canonicalUkraineRegion("Kyiv Region"))
    }
}
