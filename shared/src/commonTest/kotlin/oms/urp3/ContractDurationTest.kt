package oms.urp3

import kotlin.test.*

class ContractDurationTest {
    @Test fun sourceMonthsTakePrecedenceWithoutChangingSourceData() {
        assertEquals("6", contractDurationMonths(202, "6"))
        assertEquals("6.5", contractDurationMonths(202, " 6,5 "))
        assertEquals("0", contractDurationMonths(null, "0"))
    }
    @Test fun documentedRegressionsUseOneRoundingRule() {
        assertEquals("17", contractDurationMonths(518))
        assertEquals("21", contractDurationMonths(624))
        assertEquals("7", contractDurationMonths(202))
    }
    @Test fun zeroShortAndInvalidPeriodsAreNotInventedMonths() {
        assertEquals("0", contractDurationMonths(0))
        assertEquals("0", contractDurationMonths(15))
        assertEquals("1", contractDurationMonths(16))
        assertNull(contractDurationMonths(-1))
        assertNull(contractDurationMonths(null))
    }
    @Test fun invalidSourceFallsBackButMissingDatesStayUnavailable() {
        for (value in listOf("", "-1", "NaN", "Infinity", "unknown", "#VALUE!")) {
            assertEquals("21", contractDurationMonths(624, value))
            assertNull(contractDurationMonths(null, value))
        }
    }
    @Test fun longPeriodsDoNotOverflow() {
        assertEquals("303026596693380725", contractDurationMonths(Long.MAX_VALUE))
    }
}
