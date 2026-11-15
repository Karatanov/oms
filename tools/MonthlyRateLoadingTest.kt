package oms.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MonthlyRateLoadingTest {
    @Test fun failuresAndInvalidRatesDoNotEraseSuccessfulDates() = runBlocking {
        val result = resolveMonthlyChartRates(listOf("ok", "failed", "zero", "nan", "missing")) {
            when (it) {
                "ok" -> 50.0
                "failed" -> error("Synthetic network failure")
                "zero" -> 0.0
                "nan" -> Double.NaN
                else -> null
            }
        }
        assertEquals(mapOf("ok" to 50.0), result)
    }
    @Test fun retryCanRecoverFailedDatesWithoutRefetchingCachedSuccess() = runBlocking {
        val requested = mutableListOf<String>()
        val first = resolveMonthlyChartRates(listOf("ok", "retry")) {
            requested += it
            if (it == "retry") error("Unavailable") else 50.0
        }
        val remaining = listOf("ok", "retry").filter { it !in first }
        val second = resolveMonthlyChartRates(remaining) { requested += it; 49.0 }
        assertEquals(mapOf("ok" to 50.0, "retry" to 49.0), first + second)
        assertEquals(listOf("ok", "retry", "retry"), requested)
    }
    @Test fun batchDeadlineRetainsEarlierSuccessInsteadOfWaitingForever() = runBlocking {
        val result = resolveMonthlyChartRates(listOf("ok", "slow"), timeoutMillis = 100) {
            if (it == "slow") delay(10_000)
            50.0
        }
        assertEquals(mapOf("ok" to 50.0), result)
    }
    @Test fun navigationCancellationIsNotSwallowedAsAMissingRate() = runBlocking {
        assertFailsWith<CancellationException> {
            resolveMonthlyChartRates(listOf("cancel")) { throw CancellationException("Navigation") }
        }
        Unit
    }
}
