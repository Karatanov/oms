package oms.urp3.service

import oms.urp3.domain.FinancialRecord
import oms.urp3.domain.FinancialRecordType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinancialMoneyTest {
    private fun record(amount: Double = 0.84, currency: String = "EUR", rate: Double? = 48.125, saved: Long? = 84L) =
        FinancialRecord(1L, UUID.randomUUID(), 1L, FinancialRecordType.PAYMENT, "test", amount, currency,
            LocalDate.of(2026, 11, 12), null, null, null, "works", rate, null, saved)

    @Test fun `decimal rounding does not drop a kopeck at a half-cent boundary`() {
        assertEquals(4043L, moneyCents(0.84, 48.125))
        assertEquals(7123L, moneyCents(1.48, 48.125))
        assertEquals(101L, moneyCents(1.005))
        assertEquals(-4043L, moneyCents(-0.84, 48.125))
        assertEquals(0L, moneyCents(0.0))
        assertEquals(12345678901234567L, moneyCents(BigDecimal("123456789012345.67")))
    }

    @Test fun `display uses saved EUR and the same UAH cents for sorting and PDF`() {
        assertEquals(4043L, financialDisplayCents(record(), "UAH"))
        assertEquals(84L, financialDisplayCents(record(), "EUR"))
        assertEquals(912L, financialDisplayCents(record(100.0, "UAH", saved = 912L), "EUR"))
        assertNull(financialDisplayCents(record(100.0, "UAH", saved = null), "EUR"))
        listOf(null, 0.0, 1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(financialDisplayCents(record(rate = it), "UAH"))
        }
    }

    @Test fun `invalid and overflowing amounts stay unavailable`() {
        assertNull(moneyCents(Double.NaN))
        assertNull(moneyCents(Double.POSITIVE_INFINITY))
        assertNull(moneyCents(1.0, -48.0))
        assertNull(moneyCents(Double.MAX_VALUE))
        assertNull(financialDisplayCents(record(Double.NaN), "EUR"))
    }
}
