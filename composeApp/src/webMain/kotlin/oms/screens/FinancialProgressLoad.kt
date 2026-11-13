package oms.screens

import kotlin.coroutines.cancellation.CancellationException

/** Leaving the route or changing its scope cancels a request, not the data source. */
internal suspend fun <T> loadFinancialProgressSnapshot(request: suspend () -> T): Result<T> =
    try {
        Result.success(request())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
