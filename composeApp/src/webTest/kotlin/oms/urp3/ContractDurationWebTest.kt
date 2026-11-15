package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContractDurationWebTest {
    @Test fun durationIsIdenticalInTheBrowserIncludingLongArithmeticAndSourcePriority() {
        assertEquals("17", contractDurationMonths(518))
        assertEquals("21", contractDurationMonths(624))
        assertEquals("6", contractDurationMonths(202, "6"))
        assertEquals("0", contractDurationMonths(0))
        assertEquals("303026596693380725", contractDurationMonths(Long.MAX_VALUE))
        assertNull(contractDurationMonths(-1))
    }
}
