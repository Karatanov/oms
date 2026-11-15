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
    @Test fun `shared decimal multiplication and division agree with independent BigDecimal reference`() {
        for (rate in listOf(48.5, 48.125, 45.0, 0.999999, 0.000001, 1000000.0)) {
            for (minor in 1..1000) for (sign in listOf(-1, 1)) {
                val amount = sign * minor / 100.0
                val decimal = BigDecimal.valueOf(amount)
                val multiplier = BigDecimal.valueOf(rate)
                val product = decimal.multiply(multiplier).movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
                val quotient = decimal.divide(multiplier, 2, java.math.RoundingMode.HALF_UP)
                    .movePointRight(2).longValueExact()
                assertEquals(product, moneyCents(amount, rate), "multiply $amount at $rate")
                assertEquals(quotient, oms.urp3.decimalMoneyCents(amount, rate, true), "divide $amount at $rate")
            }
        }
    }
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
        assertEquals(208L, financialDisplayCents(record(100.0, "UAH", saved = null), "EUR"))
        assertNull(financialDisplayCents(record(100.0, "UAH", rate = null, saved = null), "EUR"))
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
    @Test fun `database decimals keep their precision and frozen conversions take precedence`() {
        assertEquals(12345678901234567L, frozenFinancialCents(BigDecimal("123456789012345.67"), "UAH", "UAH", null, null))
        assertEquals(208L, frozenFinancialCents(BigDecimal("100"), "UAH", "EUR", null, BigDecimal("48.125")))
        assertEquals(0L, frozenFinancialCents(BigDecimal("100"), "UAH", "EUR", 0, BigDecimal("48.125")))
        assertEquals(123L, frozenFinancialCents(BigDecimal("100"), "UAH", "EUR", 123, null))
        assertEquals(84L, frozenFinancialCents(BigDecimal("0.84"), "EUR", "EUR", null, null))
    }
    @Test fun `legacy EUR sentinel is unknown but UAH identity and same-currency zero remain valid`() {
        for (rate in listOf<BigDecimal?>(null, BigDecimal.ZERO, BigDecimal("-1"), BigDecimal("1.00000000"))) {
            assertNull(frozenFinancialCents(BigDecimal("100"), "EUR", "UAH", null, rate))
        }
        assertEquals(10000L, frozenFinancialCents(BigDecimal("100"), "UAH", "EUR", null, BigDecimal.ONE))
        assertEquals(0L, frozenFinancialCents(BigDecimal.ZERO, "UAH", "UAH", null, null))
        assertNull(frozenFinancialCents(BigDecimal("1E30"), "EUR", "UAH", null, BigDecimal("50")))
        assertNull(frozenFinancialCents(BigDecimal.ONE, "UAH", "EUR", null, BigDecimal("1E-30")))
        assertNull(frozenFinancialCents(BigDecimal.ONE, "UAH", "USD", null, BigDecimal.ONE))
    }
}
