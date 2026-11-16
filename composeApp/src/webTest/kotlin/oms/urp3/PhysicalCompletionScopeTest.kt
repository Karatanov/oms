package oms.urp3

import kotlin.test.*
import oms.data.ApiSubprojectProgress
import oms.screens.physicalCompletionSeries
import kotlinx.serialization.json.Json

class PhysicalCompletionScopeTest {
    private fun row(code: String, pct: Double, complete: Boolean = true) =
        ApiSubprojectProgress(code, code, code, completionPct = pct, hasCompleteAmounts = complete)
    @Test fun onlyFlaggedProjectsAreExcludedAndValidBarsRemainVisible() {
        val selected = listOf(row("valid", 30.0), row("invalid", 0.0, false))
        assertEquals(listOf(selected.first()), physicalCompletionSeries(selected).items)
        assertEquals(listOf("invalid"), physicalCompletionSeries(selected).excludedCodes)
        assertEquals(1, physicalCompletionSeries(selected).unavailableProjects)
        assertEquals(listOf(selected.first()), physicalCompletionSeries(selected.take(1)).items)
    }
    @Test fun genuineZeroAndUnclampedAggregateRemainUnchanged() {
        val rows = listOf(row("zero", 0.0), row("over", 125.0))
        assertEquals(rows, physicalCompletionSeries(rows).items)
        assertEquals(0, physicalCompletionSeries(rows).unavailableProjects)
    }
    @Test fun refreshedValidSourceRestoresDataAndNonFiniteValuesStayUnavailable() {
        assertEquals(1, physicalCompletionSeries(listOf(row("bad", Double.NaN))).unavailableProjects)
        assertEquals(0, physicalCompletionSeries(listOf(row("fixed", 20.0))).unavailableProjects)
        assertEquals(0, physicalCompletionSeries(emptyList()).unavailableProjects)
    }
    @Test fun allInvalidSelectionIsEmptyAndRefreshRestoresThePreviouslyExcludedProject() {
        val bad = row("bad", 0.0, false)
        assertTrue(physicalCompletionSeries(listOf(bad)).items.isEmpty())
        assertEquals(listOf("bad"), physicalCompletionSeries(listOf(bad)).excludedCodes)
        val refreshed = bad.copy(hasCompleteAmounts = true, completionPct = 42.0)
        assertEquals(listOf(refreshed), physicalCompletionSeries(listOf(refreshed)).items)
        assertTrue(physicalCompletionSeries(listOf(refreshed)).excludedCodes.isEmpty())
    }
    @Test fun legacyResponsesRemainReadableAndExplicitUnavailableFlagIsPreserved() {
        val old = Json.decodeFromString<ApiSubprojectProgress>("""{"projectUuid":"old","name":"Old","completionPct":0}""")
        assertEquals(0, physicalCompletionSeries(listOf(old)).unavailableProjects)
        val unavailable = Json.decodeFromString<ApiSubprojectProgress>("""{"projectUuid":"bad","name":"Bad","completionPct":0,"hasCompleteAmounts":false}""")
        assertEquals(1, physicalCompletionSeries(listOf(unavailable)).unavailableProjects)
    }
}
