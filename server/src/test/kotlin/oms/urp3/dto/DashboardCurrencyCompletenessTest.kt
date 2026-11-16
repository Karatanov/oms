package oms.urp3.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

class DashboardCurrencyCompletenessTest {
    private val response = DashboardNationalSummaryResponse(0, 0, 0, 0, 0.0, 0.0, 0, 0.0, 0)

    @Test fun `older summary payloads retain compatibility`() {
        val oldJson = Json.encodeToString(response)
        assertFalse(oldJson.contains("hasCompleteServicesPaidEurAmounts"))
        assertTrue(Json.decodeFromString<DashboardNationalSummaryResponse>(oldJson).hasCompleteServicesPaidEurAmounts)
    }

    @Test fun `unavailable EUR is explicitly marked even with default omission`() {
        val unavailable = response.copy(hasCompleteServicesPaidEurAmounts = false)
        val json = Json.encodeToString(unavailable)
        assertTrue(json.contains("\"hasCompleteServicesPaidEurAmounts\":false"))
        assertEquals(unavailable, Json.decodeFromString<DashboardNationalSummaryResponse>(json))
    }
}
