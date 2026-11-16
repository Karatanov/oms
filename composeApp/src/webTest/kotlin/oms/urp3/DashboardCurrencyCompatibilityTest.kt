package oms.urp3

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import oms.data.ApiDashboardNationalSummary
import kotlin.test.*

class DashboardCurrencyCompatibilityTest {
    private val summary = ApiDashboardNationalSummary(0, 0, 0, 0, 0.0, 0.0, 0, 0.0, 0)

    @Test fun `cached older payload retains completeness default`() {
        val json = Json.encodeToString(summary)
        assertFalse(json.contains("hasCompleteServicesPaidEurAmounts"))
        assertTrue(Json.decodeFromString<ApiDashboardNationalSummary>(json).hasCompleteServicesPaidEurAmounts)
    }

    @Test fun `new incomplete EUR payload cannot decode as complete`() {
        val json = Json.encodeToString(summary.copy(hasCompleteServicesPaidEurAmounts = false))
        assertFalse(Json.decodeFromString<ApiDashboardNationalSummary>(json).hasCompleteServicesPaidEurAmounts)
    }
}
