package oms.urp3

import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import oms.screens.loadFinancialProgressSnapshot

class FinancialProgressLoadTest {
    @Test fun availableEmptyProgressRemainsSuccessful() = runImmediate {
        val result = loadFinancialProgressSnapshot { emptyList<Int>() }
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test fun requestFailureIsAvailableForRetryPresentation() = runImmediate {
        val failure = IllegalStateException("source unavailable")
        val result = loadFinancialProgressSnapshot<Int> { throw failure }
        assertSame(failure, result.exceptionOrNull())
    }

    @Test fun cancellationIsNotConvertedIntoLoadFailure() = runImmediate {
        val cancellation = CancellationException("scope changed")
        var observed: CancellationException? = null
        try {
            loadFinancialProgressSnapshot<Int> { throw cancellation }
        } catch (failure: CancellationException) {
            observed = failure
        }
        assertSame(cancellation, observed)
    }

    // These fixtures finish synchronously: no HTTP, dispatchers or timers. Use
    // stdlib continuations so the same tests run on JS/WASM as well as the JVM.
    private fun runImmediate(block: suspend () -> Unit) {
        var completion: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        checkNotNull(completion) { "This test fixture unexpectedly suspended" }.getOrThrow()
    }
}
