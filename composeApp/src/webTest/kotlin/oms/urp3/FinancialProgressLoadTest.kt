package oms.urp3

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import oms.screens.loadFinancialProgressSnapshot

class FinancialProgressLoadTest {
    @Test fun availableEmptyProgressRemainsSuccessful() = runBlocking {
        val result = loadFinancialProgressSnapshot { emptyList<Int>() }
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test fun requestFailureIsAvailableForRetryPresentation() = runBlocking {
        val failure = IllegalStateException("source unavailable")
        val result = loadFinancialProgressSnapshot<Int> { throw failure }
        assertSame(failure, result.exceptionOrNull())
    }

    @Test fun cancellationIsNotConvertedIntoLoadFailure() = runBlocking {
        val cancellation = CancellationException("scope changed")
        var observed: CancellationException? = null
        try {
            loadFinancialProgressSnapshot<Int> { throw cancellation }
        } catch (failure: CancellationException) {
            observed = failure
        }
        assertSame(cancellation, observed)
    }
}
