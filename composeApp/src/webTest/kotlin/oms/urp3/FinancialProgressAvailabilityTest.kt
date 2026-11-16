package oms.urp3

import kotlin.test.*
import kotlinx.serialization.json.Json
import oms.data.ApiFinancialProgressItem

class FinancialProgressAvailabilityTest {
    @Test fun oldProgressPayloadStillMeansACompleteGroup() {
        val row = Json.decodeFromString<ApiFinancialProgressItem>("""{"label":"Test","contractAmountUah":1000,"completedAmountUah":100,"completionPct":10}""")
        assertTrue(row.hasCompleteAmounts)
        assertEquals(10.0, row.completionPct)
    }
    @Test fun unavailableWirePlaceholderIsFlaggedAndNeverConfusedWithAGenuineZero() {
        val unavailable = Json.decodeFromString<ApiFinancialProgressItem>("""{"label":"Test","contractAmountUah":1000,"completedAmountUah":0,"completionPct":0,"hasCompleteAmounts":false}""")
        assertFalse(unavailable.hasCompleteAmounts)
        assertTrue(unavailable.copy(hasCompleteAmounts = true).hasCompleteAmounts)
    }
}
