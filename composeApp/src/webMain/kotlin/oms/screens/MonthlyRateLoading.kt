package oms.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Keep successful dates when others fail or the batch deadline expires.
 * Navigation cancellation is different from a missing rate and must propagate.
 */
internal suspend fun resolveMonthlyChartRates(
    dates: List<String>,
    timeoutMillis: Long = 20_000,
    fetch: suspend (String) -> Double?
): Map<String, Double> {
    val resolved = mutableMapOf<String, Double>()
    withTimeoutOrNull(timeoutMillis) {
        dates.distinct().forEach { date ->
            try {
                fetch(date)?.takeIf { it.isFinite() && it > 0.0 }?.let { resolved[date] = it }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
            }
        }
    }
    return resolved
}
