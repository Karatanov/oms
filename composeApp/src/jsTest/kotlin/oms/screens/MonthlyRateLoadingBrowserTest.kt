package oms.screens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MonthlyRateLoadingBrowserTest {
    @Test fun navigationCancellationPropagates() = CoroutineScope(Dispatchers.Default).promise {
        assertFailsWith<CancellationException> {
            resolveMonthlyChartRates(listOf("cancel")) { throw CancellationException("Navigation") }
        }
        Unit
    }
    @Test fun failedLookupCanBeRetriedWithoutLosingSuccess() = CoroutineScope(Dispatchers.Default).promise {
        val first = resolveMonthlyChartRates(listOf("ok", "failed")) {
            if (it == "failed") error("Synthetic unavailable rate") else 50.0
        }
        assertEquals(mapOf("ok" to 50.0), first)
        val second = resolveMonthlyChartRates(listOf("ok", "failed").filter { it !in first }) { 49.0 }
        assertEquals(mapOf("ok" to 50.0, "failed" to 49.0), first + second)
    }
    @Test fun boundedBatchKeepsAlreadyLoadedRates() = CoroutineScope(Dispatchers.Default).promise {
        val result = resolveMonthlyChartRates(listOf("ok", "slow"), timeoutMillis = 100) {
            if (it == "slow") delay(10_000)
            50.0
        }
        assertEquals(mapOf("ok" to 50.0), result)
    }
}
