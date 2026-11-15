package oms.urp3

import oms.data.ApiFinancialRecord
import oms.data.displayAmountCents
import kotlin.test.*

class FinancialDecimalRoundingTest {
    private fun record(amount: Double, currency: String = "EUR", rate: Double? = 48.5, saved: Long? = null) =
        ApiFinancialRecord("rounding", "payment", "R", amount, currency, "2026-11-15",
            eurExchangeRate = rate, amountEurCents = saved)

    @Test fun browserUsesDecimalHalfUpForOriginalAmountsProductsAndQuotients() {
        assertEquals(1407L, record(0.29).displayAmountCents("UAH"))
        assertEquals(4043L, record(0.84, rate = 48.125).displayAmountCents("UAH"))
        assertEquals(101L, record(1.005).displayAmountCents("EUR"))
        assertEquals(-101L, record(-1.005).displayAmountCents("EUR"))
        assertEquals(101L, record(2.01, "UAH", 2.0).displayAmountCents("EUR"))
        assertEquals(-101L, record(-2.01, "UAH", 2.0).displayAmountCents("EUR"))
    }
    @Test fun savedZeroAndSavedEurPrecedeConversionWithoutMutatingTheRecord() {
        val row = record(100.0, "UAH", 50.0, 0L)
        assertEquals(0L, row.displayAmountCents("EUR", 48.0))
        assertEquals(123L, row.copy(amountEurCents = 123L).displayAmountCents("EUR"))
        assertEquals(100.0, row.amount)
        assertEquals(50.0, row.eurExchangeRate)
        assertEquals(0L, row.amountEurCents)
    }
    @Test fun unknownSentinelRatesAndOverflowDoNotProduceFakeNumbers() {
        assertNull(record(1.0, rate = null).displayAmountCents("UAH"))
        assertNull(record(1.0, rate = 1.0).displayAmountCents("UAH"))
        assertEquals(4850L, record(1.0, rate = 1.0).displayAmountCents("UAH", 48.5))
        assertNull(record(Double.MAX_VALUE).displayAmountCents("EUR"))
        assertNull(record(Double.MAX_VALUE).displayAmountCents("UAH"))
        assertNull(record(Double.NaN).displayAmountCents("EUR"))
    }
}
